package com.urbanfeet_backend.Services.Implements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbanfeet_backend.Entity.Zapatilla;
import com.urbanfeet_backend.Entity.Zapatilla_variacion;
import com.urbanfeet_backend.Model.ChatDTOs.ChatProductSummary;
import com.urbanfeet_backend.Model.ChatDTOs.ChatRequest;
import com.urbanfeet_backend.Model.ChatDTOs.ChatResponse;
import com.urbanfeet_backend.Repository.ZapatillaRepository;
import com.urbanfeet_backend.Services.Interfaces.ChatService;

@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);
    private static final Pattern PRODUCT_TAG_PATTERN = Pattern.compile("\\[VER_PRODUCTO:\\s*(\\d+)\\]");

    private final ZapatillaRepository zapatillaRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent}")
    private String geminiApiUrl;

    public ChatServiceImpl(ZapatillaRepository zapatillaRepository) {
        this.zapatillaRepository = zapatillaRepository;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);  // 5 segundos de conexión
        factory.setReadTimeout(12000);    // 12 segundos máximo de espera de respuesta
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
    }

    @Override
    @Transactional(readOnly = true)
    public ChatResponse processChat(ChatRequest request) {
        String userMessage = request.message();
        if (userMessage == null || userMessage.isBlank()) {
            return new ChatResponse("Por favor, escribe una pregunta para poder orientarte.");
        }

        // Si aún no se ha configurado la API Key de Gemini en el .env
        if (geminiApiKey == null || geminiApiKey.isBlank() || geminiApiKey.contains("tu_api_key")) {
            return new ChatResponse(
                "¡Hola! 👋 Soy UrbanBot, tu asesor de Urban Feet.\n\n" +
                "Actualmente el backend está listo esperando tu GEMINI_API_KEY en el archivo .env. " +
                "Una vez agregada, responderé en tiempo real analizando el stock de zapatillas."
            );
        }

        try {
            // 1. Obtener catálogo y variaciones reales con stock (Grounding)
            List<Zapatilla> zapatillasList = zapatillaRepository.findAll();
            String catalogContext = buildCatalogContext(zapatillasList);

            // 2. Construir System Instruction enfocada en puntualidad y acciones interactivas
            String systemInstructionText = """
                Eres UrbanBot, el asesor virtual de estilo y calzado de la tienda e-commerce Urban Feet.
                Tu función es responder de forma cercana, moderna, precisa y MUY PUNTUAL.
                
                REGLAS FUNDAMENTALES DE RESPUESTA:
                1. CONCISIÓN Y PUNTUALIDAD (MÁXIMO 1 O 2 PÁRRAFOS CORTOS):
                   - Ve directo al grano. Sé breve, amigable y claro.
                   - Si el cliente pregunta por zapatillas de cierto color, marca o talla:
                     * Si existe en la lista: confírmalo en una sola oración indicando el modelo, color y precio exacto.
                     * Si NO existe en ese color o talla exacta: dilo con amabilidad en una sola frase breve y sugiérele de inmediato 1 alternativa atractiva (máximo 2) que sí tengamos disponible en esa marca o talla.
                   - NUNCA menciones cantidades numéricas de stock (prohibido decir "tenemos 19 unidades", "stock de 5", etc.). Solo confirma que está disponible.
                   - NO menciones políticas de envío gratis a menos que el cliente te pregunte explícitamente sobre costos de envío o formas de entrega.
                   - NO listes datos internos ni modelos irrelevantes que alarguen innecesariamente el mensaje.
                   - NUNCA inventes marcas o modelos que no aparezcan en la lista de inventario adjunta.
                
                2. INTERACTIVIDAD Y APERTURA DE PRODUCTO (OBLIGATORIO):
                   - Cuando recomiendes o confirmes un modelo de zapatilla disponible de la lista, incluye al final de tu mensaje la etiqueta técnica [VER_PRODUCTO:ID] usando el número de ID correspondiente (por ejemplo: [VER_PRODUCTO:3]).
                   - Si recomiendas una alternativa adicional, puedes poner hasta 2 etiquetas (ejemplo: [VER_PRODUCTO:3] [VER_PRODUCTO:7]).
                   - La plataforma web convertirá automáticamente esta etiqueta en una tarjeta interactiva para que el cliente pueda abrir el modal del modelo, ver fotos en alta resolución, elegir su talla y añadirlo a su carrito.
                   - NUNCA menciones la palabra "ID" al cliente en el texto de tu respuesta.
                
                3. GUÍA DE TALLAS:
                   - Si preguntan por equivalencia de tallas: PE 38 = US 6 | PE 39 = US 7 | PE 40 = US 8 | PE 41 = US 8.5/9 | PE 42 = US 9.5/10 | PE 43 = US 10.5.
                
                4. SEGURIDAD Y LÍMITES:
                   - No puedes cobrar ni hacer anulaciones directas en el chat. Indica al cliente que puede hacer clic en la tarjeta del modelo sugerido para ver todas sus tallas disponibles y comprar en la web.
                
                5. TONO:
                   - Fresco, urbano y puntual. Máximo 1 o 2 párrafos concisos con emojis pertinentes (👟, 🔥, ✨).
                
                %s
                """.formatted(catalogContext);

            // 3. Preparar JSON Request Body para Gemini Flash API
            Map<String, Object> requestBody = new HashMap<>();

            // System Instruction
            Map<String, Object> systemPart = Map.of("text", systemInstructionText);
            Map<String, Object> systemInstruction = Map.of("parts", List.of(systemPart));
            requestBody.put("system_instruction", systemInstruction);

            // Contents (Mensaje del usuario)
            Map<String, Object> userPart = Map.of("text", userMessage);
            Map<String, Object> userContent = Map.of("role", "user", "parts", List.of(userPart));
            requestBody.put("contents", List.of(userContent));

            // Generation Config
            Map<String, Object> generationConfig = Map.of(
                "temperature", 0.5,
                "maxOutputTokens", 500
            );
            requestBody.put("generationConfig", generationConfig);

            // 4. Invocar API de Gemini con lista de endpoints de respaldo (Failover contra saturación)
            List<String> targetUrls = new ArrayList<>();
            targetUrls.add(buildFullUrl(geminiApiUrl));
            if (!geminiApiUrl.contains("gemini-3.5-flash-lite")) {
                targetUrls.add(buildFullUrl("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"));
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            for (String url : targetUrls) {
                try {
                    ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
                    if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                        JsonNode root = objectMapper.readTree(response.getBody());
                        JsonNode candidates = root.path("candidates");
                        if (candidates.isArray() && !candidates.isEmpty()) {
                            JsonNode textNode = candidates.get(0).path("content").path("parts").get(0).path("text");
                            if (!textNode.isMissingNode()) {
                                String rawText = textNode.asText();

                                // Extraer IDs recomendados de [VER_PRODUCTO:ID]
                                List<Integer> productIds = new ArrayList<>();
                                Matcher matcher = PRODUCT_TAG_PATTERN.matcher(rawText);
                                while (matcher.find()) {
                                    try {
                                        int id = Integer.parseInt(matcher.group(1));
                                        if (!productIds.contains(id)) {
                                            productIds.add(id);
                                        }
                                    } catch (NumberFormatException ignored) {}
                                }

                                // Limpiar etiquetas técnicas para que no salgan en el texto al usuario
                                String cleanText = PRODUCT_TAG_PATTERN.matcher(rawText).replaceAll("").trim();

                                // Armar lista de tarjetas interactivas de zapatillas sugeridas
                                List<ChatProductSummary> suggestedProducts = buildSuggestedProducts(productIds, zapatillasList);

                                return new ChatResponse(cleanText, suggestedProducts);
                            }
                        }
                    }
                } catch (HttpStatusCodeException httpEx) {
                    log.warn("Gemini endpoint falló ({}) con status {}. Probando siguiente modelo si está disponible...", 
                            url, httpEx.getStatusCode());
                } catch (ResourceAccessException timeoutEx) {
                    log.warn("Gemini endpoint excedió el timeout de espera ({}). Probando siguiente...", url);
                }
            }

            return new ChatResponse("Disculpa, en este momento el servicio de IA está experimentando alta demanda. Por favor intenta de nuevo en unos segundos.");

        } catch (Exception e) {
            log.error("Error al comunicarse con Google Gemini API: {}", e.getMessage(), e);
            return new ChatResponse(
                "¡Hola! Tuvimos un inconveniente al conectar con el servicio de IA (" + e.getClass().getSimpleName() + "). " +
                "Por favor reintenta tu consulta en unos momentos."
            );
        }
    }

    private List<ChatProductSummary> buildSuggestedProducts(List<Integer> productIds, List<Zapatilla> zapatillasList) {
        List<ChatProductSummary> result = new ArrayList<>();
        if (productIds == null || productIds.isEmpty()) {
            return result;
        }

        Map<Integer, Zapatilla> zapatillaMap = new HashMap<>();
        for (Zapatilla z : zapatillasList) {
            if (z.getId() != null) {
                zapatillaMap.put(z.getId(), z);
            }
        }

        for (Integer id : productIds) {
            Zapatilla z = zapatillaMap.get(id);
            if (z == null) {
                // Fallback opcional buscando en base de datos si no estuviera en la lista previa
                z = zapatillaRepository.findById(id).orElse(null);
            }

            if (z != null) {
                String imgUrl = null;
                Double minPrecio = null;

                if (z.getVariaciones() != null) {
                    for (Zapatilla_variacion v : z.getVariaciones()) {
                        if (imgUrl == null && v.getImageUrl() != null && !v.getImageUrl().isBlank()) {
                            imgUrl = v.getImageUrl();
                        }
                        if (v.getPrecio() != null && (minPrecio == null || v.getPrecio() < minPrecio)) {
                            minPrecio = v.getPrecio();
                        }
                    }
                }

                result.add(new ChatProductSummary(
                    z.getId(),
                    z.getNombre(),
                    z.getMarca(),
                    minPrecio != null ? minPrecio : 0.0,
                    imgUrl != null ? imgUrl : "/img/sinvar.jpg"
                ));

                if (result.size() >= 2) {
                    break;
                }
            }
        }
        return result;
    }

    private String buildFullUrl(String baseUrl) {
        if (baseUrl.contains("key=")) {
            return baseUrl;
        }
        return baseUrl + (baseUrl.contains("?") ? "&" : "?") + "key=" + geminiApiKey.trim();
    }

    /**
     * Construye un resumen en texto del inventario real disponible (stock > 0),
     * omitiendo contadores numéricos y detalles internos para que el chatbot
     * responda de forma natural y directa al cliente.
     */
    private String buildCatalogContext(List<Zapatilla> zapatillas) {
        try {
            if (zapatillas == null || zapatillas.isEmpty()) {
                return "INVENTARIO ACTUAL: El catálogo se encuentra en actualización de stock.";
            }

            StringBuilder sb = new StringBuilder("INVENTARIO ACTUALMENTE DISPONIBLE EN URBAN FEET:\n");
            int count = 0;

            for (Zapatilla z : zapatillas) {
                if (z.getVariaciones() == null || z.getVariaciones().isEmpty()) {
                    continue;
                }

                List<String> activeVariations = new ArrayList<>();
                for (Zapatilla_variacion v : z.getVariaciones()) {
                    int stock = v.getStock() != null ? v.getStock() : 0;
                    if (stock > 0) {
                        activeVariations.add("Color: " + v.getColor() + ", Talla: " + v.getTalla() + ", Precio: S/ " + v.getPrecio());
                    }
                }

                if (!activeVariations.isEmpty()) {
                    sb.append("- [ID: ").append(z.getId()).append("] ")
                      .append(z.getNombre()).append(" (Marca: ").append(z.getMarca()).append(")");
                    sb.append(" | Opciones con stock: { ").append(String.join(" | ", activeVariations)).append(" }\n");
                    count++;
                }
            }

            if (count == 0) {
                return "INVENTARIO: Catálogo multimarca de zapatillas urbanas y deportivas (Nike, Adidas, Puma, Vans, Converse).";
            }

            return sb.toString();
        } catch (Exception e) {
            log.warn("No se pudo obtener el contexto de catálogo para Gemini: {}", e.getMessage());
            return "INVENTARIO: Catálogo multimarca de zapatillas urbanas y deportivas (Nike, Adidas, Puma, Vans, Converse).";
        }
    }
}
