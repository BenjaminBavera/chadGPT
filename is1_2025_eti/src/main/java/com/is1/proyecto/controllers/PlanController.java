package com.is1.proyecto.controllers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.is1.proyecto.models.Carrera;
import com.is1.proyecto.models.Materia;
import com.is1.proyecto.models.Plan;
import com.is1.proyecto.models.PlanMateria;

import spark.Filter;
import spark.ModelAndView;
import spark.template.mustache.MustacheTemplateEngine;
import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.halt;
import static spark.Spark.before;


public class PlanController {

    // Instancia estática y final de ObjectMapper para la serialización/deserialización JSON.
    // Se inicializa una sola vez para ser reutilizada en toda la aplicación.
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void registrarRutas(){

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
        before("/crearPlan/new", filtroAdmin);

        post("/crearPlan/new", (req, res) -> {
            String carreraId = req.queryParams("carrera_id");
            String anio = req.queryParams("anio");

            try {
                Plan plan = new Plan();
                plan.set("carrera_id", Integer.parseInt(carreraId));
                plan.set("anio", Integer.parseInt(anio));
                plan.set("es_vigente", true); // Por defecto es vigente
                plan.saveIt();

                String msg = URLEncoder.encode("Plan creado correctamente.", StandardCharsets.UTF_8.toString());
                res.redirect("/crearCarrera?message=" + msg);
            } catch (Exception e) {
                String err = URLEncoder.encode("Error al crear plan: " + e.getMessage(), StandardCharsets.UTF_8.toString());
                res.redirect("/crearCarrera?error=" + err);
            }
            return "";
        });


    }
}
