package com.urbanfeet_backend.Services.Implements;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.urbanfeet_backend.DAO.Interfaces.Zapatilla_variacionDAO;
import com.urbanfeet_backend.Entity.Zapatilla_variacion;
import com.urbanfeet_backend.Model.ZapatillaDTOs.VariacionRequest;
import com.urbanfeet_backend.Model.ZapatillaDTOs.VariacionResponse;
import com.urbanfeet_backend.Model.ZapatillaDTOs.VariacionUpdateRequest;
import com.urbanfeet_backend.Services.Interfaces.Zapatilla_variacionService;
import com.urbanfeet_backend.DAO.Interfaces.ZapatillaDAO;
import com.urbanfeet_backend.Entity.Zapatilla;

@Service
public class Zapatilla_variacionServiceImpl implements Zapatilla_variacionService {

    @Autowired
    private Zapatilla_variacionDAO variacionDao;

    @Autowired
    private ZapatillaDAO zapatillaDao;

    @Override
    @Transactional
    public List<VariacionResponse> crearVariacion(Integer zapatillaId, VariacionRequest request) {
        Zapatilla zap = zapatillaDao.findById(zapatillaId);
        if (zap == null) {
            throw new RuntimeException("La zapatilla no existe.");
        }

        List<Zapatilla_variacion> nuevasVariaciones = new ArrayList<>();
        String imageUrl = blankToNull(request.imageUrl());

        for (String talla : request.tallas()) {
            Zapatilla_variacion variacion = new Zapatilla_variacion();
            variacion.setZapatilla(zap);
            variacion.setColor(request.color());
            variacion.setPrecio(request.precio());
            variacion.setStock(request.stock());
            variacion.setImageUrl(imageUrl);
            variacion.setTalla(talla);
            nuevasVariaciones.add(variacion);
        }

        List<VariacionResponse> respuestas = new ArrayList<>();
        for (Zapatilla_variacion v : nuevasVariaciones) {
            Zapatilla_variacion guardada = variacionDao.save(v);
            respuestas.add(mapToDto(guardada));
        }
        return respuestas;
    }

    @Override
    public List<Zapatilla_variacion> obtenerTodo() {
        return variacionDao.findAll();
    }

    @Override
    public Zapatilla_variacion guardar(Zapatilla_variacion variacion) {
        return variacionDao.save(variacion);
    }

    @Override
    public Zapatilla_variacion buscarPorId(Integer id) {
        return variacionDao.findById(id);
    }

    @Override
    @Transactional
    public VariacionResponse actualizar(Integer id, VariacionUpdateRequest request) {
        Zapatilla_variacion v = variacionDao.findById(id);
        if (v == null)
            throw new RuntimeException("Variación no encontrada");

        v.setColor(request.color());
        v.setTalla(request.talla());
        v.setPrecio(request.precio());
        v.setStock(request.stock());
        if (request.imageUrl() != null && !request.imageUrl().isBlank()) {
            v.setImageUrl(request.imageUrl().trim());
        }

        Zapatilla_variacion actualizada = variacionDao.update(id, v);
        return mapToDto(actualizada);
    }

    @Override
    public void eliminarPorId(Integer id) {
        variacionDao.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VariacionResponse> findByZapatillaId(Integer zapatillaId) {
        List<Zapatilla_variacion> lista = variacionDao.findByZapatillaId(zapatillaId);
        return lista.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private VariacionResponse mapToDto(Zapatilla_variacion v) {
        return new VariacionResponse(
                v.getId(),
                v.getColor(),
                v.getImageUrl(),
                v.getPrecio(),
                v.getStock(),
                v.getTalla(),
                v.getZapatilla().getId());
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    @Override
    @Transactional
    public void actualizar(Zapatilla_variacion variacion) {
        variacionDao.actualizar(variacion);
    }
}
