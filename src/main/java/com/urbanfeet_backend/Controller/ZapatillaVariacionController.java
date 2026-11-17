package com.urbanfeet_backend.Controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.urbanfeet_backend.Model.ZapatillaDTOs.VariacionRequest;
import com.urbanfeet_backend.Model.ZapatillaDTOs.VariacionResponse;
import com.urbanfeet_backend.Model.ZapatillaDTOs.VariacionUpdateRequest;
import com.urbanfeet_backend.Services.Interfaces.Zapatilla_variacionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/zapatilla-variacion")
public class ZapatillaVariacionController {

    @Autowired
    private Zapatilla_variacionService variacionService;

    @PostMapping("/zapatilla/{zapatillaId}")
    public ResponseEntity<List<VariacionResponse>> crearVariacion(
            @PathVariable Integer zapatillaId,
            @Valid @RequestBody VariacionRequest request) {
        
        return ResponseEntity.ok(variacionService.crearVariacion(zapatillaId, request));
    }

    // LISTAR POR ZAPATILLA
    @GetMapping("/zapatillas/{zapatillaId}")
    public ResponseEntity<List<VariacionResponse>> obtenerVariaciones(@PathVariable Integer zapatillaId) {
        return ResponseEntity.ok(variacionService.findByZapatillaId(zapatillaId));
    }

    // OBTENER IMAGEN BINARIA (WEBP) DIRECTA
    @GetMapping(value = "/{id}/imagen", produces = "image/webp")
    public ResponseEntity<byte[]> obtenerImagen(@PathVariable Integer id) {
        var variacion = variacionService.buscarPorId(id);
        if (variacion == null || variacion.getImageUrl() == null || variacion.getImageUrl().length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "image/webp")
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(variacion.getImageUrl());
    }

    // ACTUALIZAR (Nuevo endpoint)
    @PutMapping("/{id}")
    public ResponseEntity<VariacionResponse> actualizarVariacion(
            @PathVariable Integer id,
            @Valid @RequestBody VariacionUpdateRequest request) {

        return ResponseEntity.ok(variacionService.actualizar(id, request));
    }

    // ELIMINAR
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarVariacion(@PathVariable Integer id) {
        variacionService.eliminarPorId(id);
        return ResponseEntity.noContent().build();
    }
}
