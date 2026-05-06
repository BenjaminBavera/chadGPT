package com.is1.proyecto.controllers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.javalite.activejdbc.Base;
import org.mindrot.jbcrypt.BCrypt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.is1.proyecto.models.Carrera;
import com.is1.proyecto.models.Estudiante;
import com.is1.proyecto.models.EstudianteMateria;
import com.is1.proyecto.models.Materia;
import com.is1.proyecto.models.Plan;
import com.is1.proyecto.models.Usuario;

import spark.ModelAndView;
import static spark.Spark.get;
import static spark.Spark.post;
import spark.template.mustache.MustacheTemplateEngine;


public class EstudianteController {
    // Instancia estática y final de ObjectMapper para la serialización/deserialización JSON.
    // Se inicializa una sola vez para ser reutilizada en toda la aplicación.
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void registrarRutas(){
        get("/registrarEstudiante", (req, res) -> {
            Map<String, Object> model = new HashMap<>(); // Crea un mapa para pasar datos a la plantilla.
            String currentUsername = req.session().attribute("username");
            Boolean loggedIn = req.session().attribute("loggedIn");
            String rol = req.session().attribute("rol");
            // Validar sesión
            if (currentUsername == null || loggedIn == null || !loggedIn) {
                res.redirect("/login?error=Debes iniciar sesión para acceder a esta página.");
                return null;
            }
            // Validar rol (Solo Admins)
            if (!"administrador".equals(rol)) {
                res.redirect("/login?error=No tienes permisos para gestionar usuarios.");
                return null;
            }
            // Pasamos el nombre de usuario para que el Mustache lo salude
            model.put("username", currentUsername);
            
            // Obtener y añadir mensaje de error de los query parameters (ej. ?error=Campos vacíos)
            String successMessage = req.queryParams("message");
            if (successMessage != null && !successMessage.isEmpty()) {
                model.put("successMessage", successMessage);
            }
            
            String errorMessage = req.queryParams("error");
            if (errorMessage != null && !errorMessage.isEmpty()) {
                model.put("errorMessage", errorMessage);
            }
            // Renderiza la plantilla 'registrarEstudiante.mustache' con los datos del modelo.
            return new ModelAndView(model, "registrarEstudiante.mustache");
        }, new MustacheTemplateEngine()); // Especifica el motor de plantillas para esta ruta.


        post("/registrarEstudiante/new", (req, res) -> {
            String username = req.queryParams("username");
            String password = req.queryParams("password");
            String nombre = req.queryParams("nombre");
            String apellido = req.queryParams("apellido");
            String dni = req.queryParams("dni");
            String telefono = req.queryParams("telefono");

            if (username == null || username.isEmpty() || password == null || password.isEmpty() ||
                    nombre == null || nombre.isEmpty() || apellido == null || apellido.isEmpty() || 
                    dni == null || dni.isEmpty()) {

                res.status(400);
                res.redirect("/registrarEstudiante?error=Todos los campos obligatorios son requeridos.");
                return "";
            }

            try {
                Base.openTransaction();

                // Verificaciones
                if (Usuario.findFirst("username = ?", username) != null) {
                    throw new Exception("El nombre de usuario ya está en uso.");
                }
                if (Usuario.findFirst("dni = ?", dni) != null) {
                    throw new Exception("El DNI ya está registrado.");
                }

                // 1. Crear Padre (Usuario)
                Usuario user = new Usuario();
                user.setUsername(username);
                user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt()));
                user.setName(nombre);
                user.setApellido(apellido);
                user.setDNI(Integer.parseInt(dni));
                user.setTelefono(telefono);
                user.setRol("estudiante");
                user.saveIt();

                // 2. Crear Hijo (Estudiante)
                Estudiante est = new Estudiante();
                est.set("usuario_id", user.getId());
                est.set("anioIngreso", LocalDate.now().getYear()); // Calculamos automático
                est.set("nivel", "principiante"); // Por defecto según el CHECK de SQLite
                est.saveIt();

                Base.commitTransaction();

                res.status(201);
                String mensaje = "Estudiante " + nombre + " registrado exitosamente!";
                String mensajeCodificado = URLEncoder.encode(mensaje, StandardCharsets.UTF_8.toString());
                res.redirect("/registrarEstudiante?message=" + mensajeCodificado);
                return "";

            } catch (Exception e) {
                Base.rollbackTransaction();
                System.err.println("Error al registrar el estudiante: " + e.getMessage());
                String errorMsg = URLEncoder.encode("Error: " + e.getMessage(), StandardCharsets.UTF_8.toString());
                res.redirect("/registrarEstudiante?error=" + errorMsg);
                return "";
            }
        });
        // POST: Endpoint para añadir estudiantes (API que devuelve JSON, no HTML).
        // Advertencia: Esta ruta tiene un propósito diferente a las de formulario HTML.
        post("/add_estudiante", (req, res) -> {
            res.type("application/json"); // Establece el tipo de contenido de la respuesta a JSON.

            // Obtiene los parámetros 'name' y 'password' de la solicitud.
            String nombre = req.queryParams("nombre");
            String apellido = req.queryParams("apellido");
            String dni = req.queryParams("dni");

            // --- Validaciones de nombre y apellido ---
            if (nombre == null || nombre.isEmpty() || apellido == null || apellido.isEmpty()) {
                res.status(400); // Bad Request.
                return objectMapper.writeValueAsString(Map.of("error", "Nombre y apellido son requeridos."));
            }

            try {
                // --- Creación y guardado del usuario usando el modelo ActiveJDBC ---
                Estudiante newEstudiante = new Estudiante(); // Crea una nueva instancia de tu modelo User.

                newEstudiante.set("nombre", nombre); // Asigna el nombre al campo 'nombre'.
                newEstudiante.set("apellido", apellido); // Asigna el apellido al campo 'apellido'.
                newEstudiante.set("dni", dni);
                newEstudiante.saveIt(); // Guarda el nuevo usuario en la tabla 'estudiante'.

                res.status(201); // Created.
                // Devuelve una respuesta JSON con el mensaje y el ID del nuevo usuario.
                return objectMapper.writeValueAsString(Map.of("message", "Estudiante '" + nombre + "' registrado con éxito."));

            } catch (Exception e) {
                // Si ocurre cualquier error durante la operación de DB, se captura aquí.
                System.err.println("Error al registrar estudiante: " + e.getMessage());
                e.printStackTrace(); // Imprime el stack trace para depuración.
                res.status(500); // Internal Server Error.
                return objectMapper.writeValueAsString(Map.of("error", "Error interno al registrar estudiante: " + e.getMessage()));
            }
        });

        get("/dashboardEstudiante", (req, res)->{
            Map<String, Object> model = new HashMap<>();

            String currentUsername = req.session().attribute("currentUsername");
            model.put("username", currentUsername);

            return new ModelAndView(model, "dashboard_estudiante.mustache");
        }, new MustacheTemplateEngine());

        get("/estadoAcademico", (req, res) -> {

            Map<String, Object> model = new HashMap<>();

            Integer usuarioId = req.session().attribute("usuario_id");

            Estudiante estudiante = Estudiante.findFirst("usuario_id = ?", usuarioId);

            Object carreraId = estudiante.get("carrera_id");
            if (carreraId == null) {
                // No tiene carrera, mandamos las carreras disponibles para que elija
                model.put("carreras", Carrera.findAll());
                model.put("sinCarrera", true);
                return new ModelAndView(model, "inscripcion.mustache");
            }

            Usuario usuario = Usuario.findById(usuarioId);

            List<Map> materias = Base.findAll(
                    "SELECT m.nombre, em.estado, em.materia_codigo " +
                            "FROM estudiante_materia em " +
                            "JOIN materia m ON em.materia_codigo = m.id " +
                            "WHERE em.estudiante_id = ?",
                    estudiante.getId()
            );

            Map<String, Object> usuarioData = new HashMap<>();
            usuarioData.put("nombre", usuario.getString("nombre"));
            usuarioData.put("apellido", usuario.getString("apellido"));
            usuarioData.put("dni", usuario.getString("dni"));
            
            model.put("usuario", usuarioData);
            model.put("estudiante", estudiante);
            model.put("materias", materias);

            return new ModelAndView(model, "estado_academico.mustache");

        }, new MustacheTemplateEngine());


        get("/perfil", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            Integer usuarioId = req.session().attribute("usuario_id");
            Usuario user = Usuario.findById(usuarioId);
            model.put("usuario", user);
            String message = req.queryParams("message");
            String error = req.queryParams("error");
            if (message != null) model.put("message", message);
            if (error != null) model.put("error", error);
            return new ModelAndView(model, "perfil_estudiante.mustache");
        }, new MustacheTemplateEngine());

        post("/cambiarPassword", (req, res) -> {
            int usuarioId = req.session().attribute("usuario_id");
            String actual = req.queryParams("actual");
            String nueva = req.queryParams("nueva");
            Usuario user = Usuario.findById(usuarioId);
            if (nueva.length() < 4) {
                String err = URLEncoder.encode("Contraseña muy corta", StandardCharsets.UTF_8.toString());
                res.redirect("/perfil?error=" + err);
                return null;
            }
            if (!BCrypt.checkpw(actual, user.getPassword())) {
                String err = URLEncoder.encode("Contraseña actual incorrecta", StandardCharsets.UTF_8.toString());
                res.redirect("/perfil?error=" + err);
                return null;
            }
            user.setPassword(BCrypt.hashpw(nueva, BCrypt.gensalt()));
            user.saveIt();
            String msg = URLEncoder.encode("Contraseña actualizada correctamente", StandardCharsets.UTF_8.toString());
            res.redirect("/perfil?message=" + msg);
            return null;
        });

        get("/inscripcion", (req,res) -> {
            // 1. Verificar que haya iniciado sesión
            Boolean loggedIn = req.session().attribute("loggedIn");
            if (loggedIn == null || !loggedIn) {
                res.redirect("/login?error=Debes iniciar sesión para acceder a esta página.");
                    return null;
            }
            
            // 2. Verificar que el rol sea ESPECÍFICAMENTE "estudiante"
            String rolUsuario = req.session().attribute("rol");
            // Asumimos que guardaste el rol en minúsculas en la BD
            if (rolUsuario == null || !rolUsuario.equals("estudiante")) {
                System.out.println("DEBUG: Intento de acceso denegado a /inscripcion por rol: " + rolUsuario);
                // Lo mandamos al dashboard con un mensaje de error
                res.redirect("/dashboard?error=Acceso denegado. Esta sección es exclusiva para estudiantes.");
                return null;
            }

            Map<String, Object> model = new HashMap<>();
            Integer usuarioId = req.session().attribute("usuario_id");
            Estudiante estudiante = Estudiante.findFirst("usuario_id = ?", usuarioId);
            Usuario usuario = Usuario.findById(usuarioId);

            Map<String, Object> usuarioData = new HashMap<>();
            usuarioData.put("nombre", usuario.getString("nombre"));
            usuarioData.put("apellido", usuario.getString("apellido"));
            usuarioData.put("dni", usuario.getString("dni"));
            model.put("usuario", usuarioData);

            // ← CHEQUEO CLAVE
            Object carreraId = estudiante.get("carrera_id");
            if (carreraId == null) {
                model.put("carreras", Carrera.findAll());
                model.put("sinCarrera", true);
                String error = req.queryParams("error");
                if (error != null && !error.isEmpty()) {
                    model.put("errorMessage", error);
                }

                String success = req.queryParams("successMessage");
                if (success != null && !success.isEmpty()) {
                    model.put("successMessage", success);
                }
                return new ModelAndView(model, "inscripcion.mustache");
            }

            // Si tiene carrera, cargamos las materias
            List<Map> inscripciones = Base.findAll(
                "SELECT m.id, m.nombre, m.anio_cursado, m.cuatrimestre, em.estado " +
                "FROM estudiante_materia em " +
                "JOIN materia m ON em.materia_codigo = m.id " +
                "WHERE em.estudiante_id = ?",
                estudiante.getId()
            );

            List<Materia> materiasDisponibles = Materia.findBySQL(
                "SELECT m.* FROM materia m " +
                "WHERE m.plan_id IN (SELECT id FROM plan WHERE carrera_id = ?) " +
                "AND m.id NOT IN (SELECT materia_codigo FROM estudiante_materia WHERE estudiante_id = ?) " +
                "AND NOT EXISTS (" +
                "   SELECT 1 FROM correlatividad c " +
                "   WHERE c.materia_codigo = m.id " +
                "   AND NOT EXISTS (" +
                "       SELECT 1 FROM estudiante_materia em " +
                "       WHERE em.estudiante_id = ? " +
                "       AND em.materia_codigo = c.correlativa_codigo " +
                "       AND (" +
                "           (c.tipo = 'regular' AND em.estado IN ('regular', 'aprobada')) " +
                "           OR (c.tipo = 'aprobada' AND em.estado = 'aprobada')" +
                "       )" +
                "   )" +
                ")",
                carreraId, estudiante.getId(), estudiante.getId()
            );

            model.put("materiasInscribir", materiasDisponibles);
            model.put("materiasMatriculadas", inscripciones);

            Plan plan = Plan.findFirst("carrera_id = ?", carreraId);
            if (plan != null) {
                Carrera carrera = Carrera.findById(plan.get("carrera_id"));
                Map<String, Object> planData = new HashMap<>();
                planData.put("anio", plan.get("anio"));
                planData.put("nombre", carrera != null ? carrera.get("nombre") : "Sin carrera");
                model.put("plan", planData);
            }

            String error = req.queryParams("error");
            if (error != null && !error.isEmpty()) {
                model.put("errorMessage", error);
            }

            String success = req.queryParams("successMessage");
            if (success != null && !success.isEmpty()) {
                model.put("successMessage", success);
            }

            model.put("estudianteLogueado", estudiante);
            return new ModelAndView(model, "inscripcion.mustache");
        }, new MustacheTemplateEngine());

         post("/inscribir", (req, res) -> {
            
            int materiaID = Integer.parseInt(req.queryParams("materia_id"));
            Integer usuarioId = req.session().attribute("usuario_id");
            Estudiante estudiante = Estudiante.findFirst("usuario_id = ?", usuarioId);
            int estudianteID = ((Number) estudiante.getId()).intValue();

            // Verificar si ya está inscripto
            EstudianteMateria existente = EstudianteMateria.findFirst(
            "estudiante_id = ? AND materia_codigo = ?", estudianteID, materiaID);

            if (existente != null) {
                String msg = URLEncoder.encode("Ya estás inscripto en esta materia.", "UTF-8");
                res.redirect("/inscripcion?error=" + msg);
                return null;
            }

            // Validar correlativas
            List<Map> correlativas = Base.findAll(
                "SELECT c.correlativa_codigo, c.tipo, m.nombre " +
                "FROM correlatividad c " +
                "JOIN materia m ON c.correlativa_codigo = m.id " +
                "WHERE c.materia_codigo = ?",
                materiaID
            );

            for (Map corr : correlativas) {
                int correlativaCodigo = ((Number) corr.get("correlativa_codigo")).intValue();
                String tipo = corr.get("tipo").toString();
                String nombreCorrelativa = corr.get("nombre").toString();

                EstudianteMateria estadoCorr = EstudianteMateria.findFirst(
                "estudiante_id = ? AND materia_codigo = ?", estudianteID, correlativaCodigo);

                boolean cumple = false;
                if (estadoCorr != null) {
                    String estado = estadoCorr.getString("estado");

                    if (tipo.equals("regular") && (estado.equals("regular") || estado.equals("aprobada"))) {
                        cumple = true;
                    } else if (tipo.equals("aprobada") && estado.equals("aprobada")) {
                        cumple = true;
                    }
                }

                if (!cumple) {
                    String condicion = tipo.equals("regular") ? "regularizada" : "aprobada";
                    String error = "No podés inscribirte. Necesitás tener " + condicion + ": " + nombreCorrelativa;
                    res.redirect("/inscripcion?error=" + URLEncoder.encode(error, "UTF-8"));
                    return null;
                }
            }

            // Si pasa todas las validaciones, inscribir
            EstudianteMateria inscripcion = new EstudianteMateria();
            inscripcion.set("estudiante_id", estudianteID);
            inscripcion.set("materia_codigo", materiaID);
            inscripcion.set("estado", "inscripto");
            inscripcion.saveIt();
            res.redirect("/inscripcion?successMessage=" + URLEncoder.encode("Inscripción exitosa", "UTF-8"));
            return null;
        }); 

        post("/inscribirCarrera", (req, res) -> {
            String carreraId = req.queryParams("carrera_id");
            Integer usuarioId = req.session().attribute("usuario_id");

            if (carreraId == null || carreraId.isEmpty()) {
                res.redirect("/inscripcion?errorMessage=Debés seleccionar una carrera.");
                return null;
            }

            try {
                Estudiante estudiante = Estudiante.findFirst("usuario_id = ?", usuarioId);
                estudiante.set("carrera_id", Integer.parseInt(carreraId));
                estudiante.saveIt();
                res.redirect("/inscripcion");
            } catch (Exception e) {
                System.err.println("Error al inscribir carrera: " + e.getMessage());
                res.redirect("/inscripcion?errorMessage=Error al guardar la carrera.");
            }
            return null;
        });

    }
}
