package com.is1.proyecto.controllers;

import com.is1.proyecto.models.Materia;

import spark.Filter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import static spark.Spark.post;
import static spark.Spark.halt;
import static spark.Spark.before;


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
    }
}