package com.is1.proyecto.controllers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.javalite.activejdbc.Base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.is1.proyecto.models.Carrera;
import com.is1.proyecto.models.Materia;
import com.is1.proyecto.models.Plan;

import spark.Filter;
import spark.ModelAndView;
import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.halt;
import static spark.Spark.before;
import spark.template.mustache.MustacheTemplateEngine;

public class CarreraController {
    // Instancia estática y final de ObjectMapper para la serialización/deserialización JSON.
    // Se inicializa una sola vez para ser reutilizada en toda la aplicación.
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void registrarRutas(){

        //Definición del filtro de seguridad.
        Filter filtroAdminCarreras = (req, res) -> {
            Boolean loggedIn = req.session().attribute("loggedIn");
            String rol = req.session().attribute("rol");

            if (loggedIn == null || !loggedIn) {
                res.redirect("/?error=" + URLEncoder.encode("Debes iniciar sesión primero.", StandardCharsets.UTF_8));
                halt(); 
            } else if (!"administrador".equals(rol)) {
                res.redirect("/dashboard?error=" + URLEncoder.encode("Acceso denegado. Solo administradores.", StandardCharsets.UTF_8));
                halt(); 
            }
        };

        //Aplicación del filtro a las rutas.
        before("/crearCarrera", filtroAdminCarreras);
        before("/crearCarrera/new", filtroAdminCarreras);
        before("/inscriptosPorMateria", filtroAdminCarreras);
        before("/inscriptosPorMateria/*", filtroAdminCarreras); 
        before("/actualizarCupo", filtroAdminCarreras);

        get("/crearCarrera", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            String currentUsername = req.session().attribute("username");
            model.put("username", currentUsername);
            // --- LA MAGIA NUEVA: Traemos los datos de la BD ---
            model.put("carreras", Carrera.findAll());
            
            List<Plan> planesRaw = Plan.findAll();
            List<Map<String, Object>> planes = new ArrayList<>();
            for (Plan p : planesRaw) {
                Map<String, Object> planData = new HashMap<>();
                planData.put("id", p.getId());
                planData.put("anio", p.get("anio"));
                Carrera c = Carrera.findById(p.get("carrera_id"));
                planData.put("carreraNombre", c != null ? c.get("nombre") : "Sin carrera");
                planes.add(planData);
            }
            model.put("planes", planes);

            model.put("materias", Materia.findAll());
            // --------------------------------------------------
            String successMessage = req.queryParams("message");
            if (successMessage != null) model.put("successMessage", successMessage);
            String errorMessage = req.queryParams("error");
            if (errorMessage != null) model.put("errorMessage", errorMessage);
            return new ModelAndView(model, "crear_carrera.mustache");
        }, new MustacheTemplateEngine());

        post("/crearCarrera/new", (req, res) -> {
            // 1. Capturar el dato del formulario
            String nombre = req.queryParams("nombre");
            // 2. Validación de campos vacíos
            if (nombre == null || nombre.trim().isEmpty()) {
                res.redirect("/crearCarrera?error=El nombre de la carrera es obligatorio.");
                return "";
            }
            try {
                // 3. Regla de Negocio: Evitar carreras duplicadas
                if (Carrera.findFirst("nombre = ?", nombre.trim()) != null) {
                    throw new Exception("Ya existe una carrera registrada con ese nombre.");
                }
                // 4. Guardar en Base de Datos
                Carrera carrera = new Carrera();
                carrera.set("nombre", nombre.trim()); // .trim() saca espacios en blanco accidentales
                carrera.saveIt();
                // 5. Redirigir con éxito
                String mensaje = "La carrera '" + nombre + "' se creó correctamente.";
                String msgEncoded = URLEncoder.encode(mensaje, StandardCharsets.UTF_8.toString());
                res.redirect("/crearCarrera?message=" + msgEncoded);
            } catch (Exception e) {
                // Manejo de errores
                System.err.println("Error al crear carrera: " + e.getMessage());
                String errorEncoded = URLEncoder.encode("Error: " + e.getMessage(), StandardCharsets.UTF_8.toString());
                res.redirect("/crearCarrera?error=" + errorEncoded);
            }
            return "";
        });

        get("/inscriptosPorMateria", (req, res) -> {
            Map<String, Object> model = new HashMap<>();

            List<Map> materias = Base.findAll(
                "SELECT m.id, m.nombre, m.cupo, COUNT(em.estudiante_id) AS inscriptos, " +
                "(m.cupo - COUNT(em.estudiante_id)) AS disponibles " +
                "FROM materia m " +
                "LEFT JOIN estudiante_materia em ON m.id = em.materia_codigo " +
                "GROUP BY m.id, m.nombre, m.cupo"
            );

            model.put("materias", materias);

            String error = req.queryParams("error");
            if (error != null) model.put("error", error);
            String message = req.queryParams("message");
            if (message != null) model.put("message", message);

            return new ModelAndView(model, "inscriptos_por_materia.mustache");
        }, new MustacheTemplateEngine());

        get("/inscriptosPorMateria/:materiaId", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            int materiaId = Integer.parseInt(req.params("materiaId"));

            Materia materia = Materia.findById(materiaId);

            Map<String, Object> materiaData = new HashMap<>();
            materiaData.put("id", materia.getId());
            materiaData.put("nombre", materia.getString("nombre"));
            materiaData.put("cupo", materia.get("cupo"));
            model.put("materia", materiaData);

            List<Map> estudiantes = Base.findAll(
                "SELECT u.nombre, u.apellido, u.dni, em.estado, em.nota " +
                "FROM estudiante_materia em " +
                "JOIN estudiante e ON em.estudiante_id = e.id " +
                "JOIN usuario u ON e.usuario_id = u.id " +
                "WHERE em.materia_codigo = ?",
                materiaId
            );
            model.put("estudiantes", estudiantes);

            String error = req.queryParams("error");
            if (error != null) model.put("error", error);
            String message = req.queryParams("message");
            if (message != null) model.put("message", message);

            return new ModelAndView(model, "detalle_materia.mustache");
        }, new MustacheTemplateEngine());

        post("/actualizarCupo", (req, res) -> {
            String materiaId = req.queryParams("materia_id");
            String cupo = req.queryParams("cupo");

            try {
                Base.exec(
                    "UPDATE materia SET cupo = ? WHERE id = ?",
                    Integer.parseInt(cupo), Integer.parseInt(materiaId)
                );
                String msg = URLEncoder.encode("Cupo actualizado correctamente.", StandardCharsets.UTF_8.toString());
                res.redirect("/inscriptosPorMateria/" + materiaId + "?message=" + msg);
            } catch (Exception e) {
                String err = URLEncoder.encode("Error al actualizar el cupo.", StandardCharsets.UTF_8.toString());
                res.redirect("/inscriptosPorMateria/" + materiaId + "?error=" + err);
            }
            return null;
        });
    }
}
