package com.is1.proyecto.controllers;

import com.is1.proyecto.models.Materia;

import spark.Filter;
import spark.ModelAndView;
import spark.template.mustache.MustacheTemplateEngine;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static spark.Spark.post;
import static spark.Spark.halt;
import static spark.Spark.before;
import static spark.Spark.get;

import org.javalite.activejdbc.Base;


public class MateriaController {

    public static void registrarRutas() {

        //Definición del filtro de seguridad.
        Filter filtroAdmin = (req, res) -> {
            Boolean loggedIn = req.session().attribute("loggedIn");
            String rol = req.session().attribute("rol");

            if (loggedIn == null || !loggedIn) {
                res.redirect("/login?error=" + URLEncoder.encode("Debes iniciar sesión primero.", StandardCharsets.UTF_8));
                halt(); 
            } else if (!"administrador".equals(rol)) {
                res.redirect("/dashboard?error=" + URLEncoder.encode("Acceso denegado. Solo administradores.", StandardCharsets.UTF_8));
                halt(); 
            }
        };

        //Aplicación del filtro a las rutas.
        before("/crearMateria/new", filtroAdmin);
        before("/eliminarMateria", filtroAdmin);
        before("/asignarCorrelativas", filtroAdmin);
        before("/asignarCorrelativa", filtroAdmin);
        before("/eliminarCorrelativa", filtroAdmin);

        post("/crearMateria/new", (req, res) -> {
            String planId = req.queryParams("plan_id");
            String nombre = req.queryParams("nombre");
            String anioCursado = req.queryParams("anio_cursado");
            String cuatrimestre = req.queryParams("cuatrimestre");

            try {
                Materia materia = new Materia();
                materia.set("plan_id", Integer.parseInt(planId));
                materia.set("nombre", nombre.trim());
                materia.set("anio_cursado", Integer.parseInt(anioCursado));
                materia.set("cuatrimestre", Integer.parseInt(cuatrimestre));
                materia.saveIt();

                String msg = URLEncoder.encode("Materia creada y vinculada con éxito.", StandardCharsets.UTF_8.toString());
                res.redirect("/crearCarrera?message=" + msg);
            } catch (Exception e) {
                String err = URLEncoder.encode("Error al crear materia: " + e.getMessage(), StandardCharsets.UTF_8.toString());
                res.redirect("/crearCarrera?error=" + err);
            }
            return "";
        });

        post("/eliminarMateria", (req, res) -> {
            String materiaId = req.queryParams("materia_id");

            try {
                Materia materia = Materia.findById(materiaId);
                if (materia != null) {
                    materia.delete();
                    String msg = URLEncoder.encode("Materia eliminada.", StandardCharsets.UTF_8.toString());
                    res.redirect("/crearCarrera?message=" + msg);
                } else {
                    res.redirect("/crearCarrera?error=No se encontró la materia.");
                }
            } catch (Exception e) {
                res.redirect("/crearCarrera?error=Error al eliminar la materia.");
            }
            return "";
        });

        get("/asignarCorrelativas", (req, res) -> {
            Map<String, Object> model = new HashMap<>();

            // 1. Atajar mensajes de la URL (éxito o error)
            String error = req.queryParams("error");
            if (error != null) model.put("errorMessage", error);
            String success = req.queryParams("message");
            if (success != null) model.put("successMessage", success);

            // 2. Traer TODAS las materias para llenar los combos desplegables
            model.put("materias", Materia.findAll().orderBy("nombre ASC"));

            // 3. Traer la lista de correlatividades actuales cruzando datos para tener los nombres
            String sql = "SELECT c.materia_codigo, m1.nombre AS materia_nombre, " +
                         "c.correlativa_codigo, m2.nombre AS correlativa_nombre, c.tipo " +
                         "FROM correlatividad c " +
                         "JOIN materia m1 ON c.materia_codigo = m1.id " +
                         "JOIN materia m2 ON c.correlativa_codigo = m2.id " +
                         "ORDER BY m1.nombre ASC";
            
            List<Map> listaCorrelatividades = Base.findAll(sql);
            
            // Pequeño truco para Mustache: le agregamos un booleano para pintar de color la etiqueta
            for (Map fila : listaCorrelatividades) {
                boolean esAprobada = "aprobada".equalsIgnoreCase((String) fila.get("tipo"));
                fila.put("esAprobada", esAprobada);
            }
            
            model.put("correlatividades", listaCorrelatividades);

            return new ModelAndView(model, "asignar_correlativas.mustache");
        }, new MustacheTemplateEngine());

        post("/asignarCorrelativa", (req, res) -> {
            // materia_codigo: La materia que el alumno quiere cursar (Ej: Algoritmos II)
            // correlativa_codigo: La materia que es requisito previo (Ej: Algoritmos I)
            String materiaCodigo = req.queryParams("materia_codigo"); 
            String correlativaCodigo = req.queryParams("correlativa_codigo"); 
            String tipo = req.queryParams("tipo"); // Debe ser 'regular' o 'aprobada'

            try {
                // Validación 1: Una materia no puede ser correlativa de sí misma
                if (materiaCodigo.equals(correlativaCodigo)) {
                    res.redirect("/asignarCorrelativas?error=" + URLEncoder.encode("Una materia no puede ser correlativa de sí misma.", StandardCharsets.UTF_8.toString()));
                    return "";
                }

                // Validación 2: Verificar que no se haya cargado ya esa misma relación
                Number existe = (Number) Base.firstCell(
                    "SELECT count(*) FROM correlatividad WHERE materia_codigo = ? AND correlativa_codigo = ?", 
                    materiaCodigo, correlativaCodigo
                );
                
                if (existe != null && existe.intValue() > 0) {
                    res.redirect("/asignarCorrelativas?error=" + URLEncoder.encode("Esta correlatividad ya está asignada.", StandardCharsets.UTF_8.toString()));
                    return "";
                }

                // Insertamos la relación en la base de datos
                Base.exec("INSERT INTO correlatividad (materia_codigo, correlativa_codigo, tipo) VALUES (?, ?, ?)", 
                          materiaCodigo, correlativaCodigo, tipo);

                String msg = URLEncoder.encode("Correlativa asignada con éxito.", StandardCharsets.UTF_8.toString());
                res.redirect("/asignarCorrelativas?message=" + msg);
                
            } catch (Exception e) {
                System.err.println("Error al asignar correlativa: " + e.getMessage());
                String err = URLEncoder.encode("Error al guardar la correlatividad.", StandardCharsets.UTF_8.toString());
                res.redirect("/asignarCorrelativas?error=" + err);
            }
            return "";
        });

        post("/eliminarCorrelativa", (req, res) -> {
            String materiaCodigo = req.queryParams("materia_codigo");
            String correlativaCodigo = req.queryParams("correlativa_codigo");

            try {
                int filasBorradas = Base.exec(
                    "DELETE FROM correlatividad WHERE materia_codigo = ? AND correlativa_codigo = ?", 
                    materiaCodigo, correlativaCodigo
                );

                if (filasBorradas > 0) {
                    String msg = URLEncoder.encode("Correlativa eliminada correctamente.", StandardCharsets.UTF_8.toString());
                    res.redirect("/crearCarrera?message=" + msg);
                } else {
                    res.redirect("/crearCarrera?error=" + URLEncoder.encode("No se encontró la correlatividad para eliminar.", StandardCharsets.UTF_8.toString()));
                }
            } catch (Exception e) {
                System.err.println("Error al eliminar correlativa: " + e.getMessage());
                res.redirect("/crearCarrera?error=" + URLEncoder.encode("Error interno al eliminar.", StandardCharsets.UTF_8.toString()));
            }
            return "";
        });
    }
}