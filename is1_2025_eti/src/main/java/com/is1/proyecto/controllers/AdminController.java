package com.is1.proyecto.controllers;

import static spark.Spark.get;
import static spark.Spark.post;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.javalite.activejdbc.Base;
import org.mindrot.jbcrypt.BCrypt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.is1.proyecto.models.User;
import com.is1.proyecto.models.Usuario;

import spark.ModelAndView;
import spark.template.mustache.MustacheTemplateEngine;

public class AdminController {

    // Instancia estática y final de ObjectMapper para la serialización/deserialización JSON.
    // Se inicializa una sola vez para ser reutilizada en toda la aplicación.
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void registrarRutas(){
        // --- Rutas GET para renderizar formularios y páginas HTML ---

        // GET: Muestra el formulario de creación de cuenta.
        // Soporta la visualización de mensajes de éxito o error pasados como query parameters.
        get("/user/create", (req, res) -> {
            Map<String, Object> model = new HashMap<>(); // Crea un mapa para pasar datos a la plantilla.

            // Obtener y añadir mensaje de éxito de los query parameters (ej. ?message=Cuenta creada!)
            String successMessage = req.queryParams("message");
            if (successMessage != null && !successMessage.isEmpty()) {
                model.put("successMessage", successMessage);
            }

            // Obtener y añadir mensaje de error de los query parameters (ej. ?error=Campos vacíos)
            String errorMessage = req.queryParams("error");
            if (errorMessage != null && !errorMessage.isEmpty()) {
                model.put("errorMessage", errorMessage);
            }

            // Renderiza la plantilla 'user_form.mustache' con los datos del modelo.
            return new ModelAndView(model, "user_form.mustache");
        }, new MustacheTemplateEngine()); // Especifica el motor de plantillas para esta ruta.

        // GET: Ruta para mostrar el dashboard (panel de control) del usuario.
        // Requiere que el usuario esté autenticado.
        get("/dashboard", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            String username = req.session().attribute("username");
            Boolean loggedIn = req.session().attribute("loggedIn");
            String rol = req.session().attribute("rol");

            // Validación de sesión
            if (loggedIn == null || !loggedIn || username == null) {
                res.redirect("/?error=Inicia sesión primero");
                return null;
            }
            // Validación de Rol (solo permitimos al admin aquí)
            if (!"administrador".equals(rol)) {
                res.redirect("/?error=No tienes permisos de administrador");
                return null;
            }
            model.put("username", username);
            // Asegúrate de que el nombre del archivo coincida con el que tenías para el admin
            return new ModelAndView(model, "dashboard.mustache");
        }, new MustacheTemplateEngine());

        // GET: Ruta para cerrar la sesión del usuario.
        get("/logout", (req, res) -> {
            // Invalida completamente la sesión del usuario.
            // Esto elimina todos los atributos guardados en la sesión y la marca como inválida.
            // La cookie JSESSIONID en el navegador también será gestionada para invalidarse.
            req.session().invalidate();

            System.out.println("DEBUG: Sesión cerrada. Redirigiendo a /login.");

            // Redirige al usuario a la página de login con un mensaje de éxito.
            res.redirect("/");

            return null; // Importante retornar null después de una redirección.
        });

        // GET: Muestra el formulario de inicio de sesión (login).
        // Nota: Esta ruta debería ser capaz de leer también mensajes de error/éxito de los query params
        // si se la usa como destino de redirecciones. (Tu código de /user/create ya lo hace, aplicar similar).
        get("/", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            String errorMessage = req.queryParams("error");
            if (errorMessage != null && !errorMessage.isEmpty()) {
                model.put("errorMessage", errorMessage);
            }
            String successMessage = req.queryParams("message");
            if (successMessage != null && !successMessage.isEmpty()) {
                model.put("successMessage", successMessage);
            }
            return new ModelAndView(model, "login.mustache");
        }, new MustacheTemplateEngine()); // Especifica el motor de plantillas para esta ruta.

        // GET: Ruta de alias para el formulario de creación de cuenta.
        // En una aplicación real, probablemente querrías unificar con '/user/create' para evitar duplicidad.
        get("/user/new", (req, res) -> {
            return new ModelAndView(new HashMap<>(), "user_form.mustache"); // No pasa un modelo específico, solo el formulario.
        }, new MustacheTemplateEngine()); // Especifica el motor de plantillas para esta ruta.

        // GET: Muestra el formulario para registrar un nuevo Administrador
        get("/crearAdmin", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            // 1. Validar la sesión y el rol actual
            String currentUsername = req.session().attribute("username");
            Boolean loggedIn = req.session().attribute("loggedIn");
            String rol = req.session().attribute("rol");
            if (currentUsername == null || loggedIn == null || !loggedIn) {
                res.redirect("/?error=Debes iniciar sesión para acceder a esta página.");
                return null;
            }
            // ¡SEGURIDAD! Solo un administrador puede crear otro administrador
            if (!"administrador".equals(rol)) {
                System.out.println("DEBUG: Intento de acceso denegado a /crearAdmin por rol: " + rol);
                // Lo mandamos a su panel correspondiente con un reto
                res.redirect("/dashboard?error=No tienes permisos para registrar administradores.");
                return null;
            }
            // 2. Capturar mensajes de éxito o error que vienen del POST
            String successMessage = req.queryParams("message");
            if (successMessage != null && !successMessage.isEmpty()) {
                model.put("successMessage", successMessage);
            }
            String errorMessage = req.queryParams("error");
            if (errorMessage != null && !errorMessage.isEmpty()) {
                model.put("errorMessage", errorMessage);
            }
            // 3. Renderizar la plantilla HTML
            return new ModelAndView(model, "crear_admin.mustache");
        }, new MustacheTemplateEngine());

        // --- Rutas POST para manejar envíos de formularios y APIs ---
        //Metodo post para crear un admin
        post("/admin/new", (req, res) -> {
            String username = req.queryParams("username");
            String password = req.queryParams("password");
            String nombre = req.queryParams("nombre");
            String apellido = req.queryParams("apellido");
            String dni = req.queryParams("dni");
            String telefono = req.queryParams("telefono");

            // Validaciones
            if (username == null || username.isEmpty() || password == null || password.isEmpty() ||
                    nombre == null || nombre.isEmpty() || apellido == null || apellido.isEmpty() || dni == null || dni.isEmpty()) {

                res.status(400);
                res.redirect("/crearAdmin?error=Todos los campos obligatorios son requeridos.");
                return "";
            }

            try {
                // Verificar duplicados
                if (Usuario.findFirst("username = ?", username) != null) {
                    throw new Exception("El nombre de usuario ya está en uso.");
                }
                if (Usuario.findFirst("dni = ?", dni) != null) {
                    throw new Exception("El DNI ya está registrado.");
                }

                // Crear la entidad
                Usuario admin = new Usuario();
                admin.setUsername(username);
                admin.setPassword(BCrypt.hashpw(password, BCrypt.gensalt())); // Hasheamos!
                admin.setName(nombre);
                admin.setApellido(apellido);
                admin.setDNI(Integer.parseInt(dni));
                admin.setTelefono(telefono);

                // ¡LA CLAVE! Forzamos el rol desde el backend, el usuario no elige.
                admin.setRol("administrador");

                admin.saveIt(); // Guardamos en DB

                res.status(201);
                String mensaje = "Administrador " + nombre + " registrado exitosamente!";
                String mensajeCodificado = URLEncoder.encode(mensaje, StandardCharsets.UTF_8.toString());
                // Redirigimos de vuelta al formulario (o al dashboard, como prefieras)
                res.redirect("/crearAdmin?message=" + mensajeCodificado);
                return "";

            } catch (Exception e) {
                System.err.println("Error al registrar el admin: " + e.getMessage());
                String errorMsg = URLEncoder.encode("Error: " + e.getMessage(), StandardCharsets.UTF_8.toString());
                res.redirect("/crearAdmin?error=" + errorMsg);
                return "";
            }
        });

        // POST: Maneja el envío del formulario de creación de nueva cuenta.
        post("/user/new", (req, res) -> {
            String name = req.queryParams("name");
            String password = req.queryParams("password");

            // Validaciones básicas: campos no pueden ser nulos o vacíos.
            if (name == null || name.isEmpty() || password == null || password.isEmpty()) {
                res.status(400); // Código de estado HTTP 400 (Bad Request).
                // Redirige al formulario de creación con un mensaje de error.
                res.redirect("/user/create?error=Nombre y contraseña son requeridos.");
                return ""; // Retorna una cadena vacía ya que la respuesta ya fue redirigida.
            }

            try {
                // Intenta crear y guardar la nueva cuenta en la base de datos.
                User ac = new User(); // Crea una nueva instancia del modelo User.
                // Hashea la contraseña de forma segura antes de guardarla.
                String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

                ac.set("name", name); // Asigna el nombre de usuario.
                ac.set("password", hashedPassword); // Asigna la contraseña hasheada.
                ac.saveIt(); // Guarda el nuevo usuario en la tabla 'users'.

                res.status(201); // Código de estado HTTP 201 (Created) para una creación exitosa.
                // Redirige al formulario de creación con un mensaje de éxito.
                res.redirect("/user/create?message=Cuenta creada exitosamente para " + name + "!");
                return ""; // Retorna una cadena vacía.

            } catch (Exception e) {
                // Si ocurre cualquier error durante la operación de DB (ej. nombre de usuario duplicado),
                // se captura aquí y se redirige con un mensaje de error.
                System.err.println("Error al registrar la cuenta: " + e.getMessage());
                e.printStackTrace(); // Imprime el stack trace para depuración.
                res.status(500); // Código de estado HTTP 500 (Internal Server Error).
                res.redirect("/user/create?error=Error interno al crear la cuenta. Intente de nuevo.");
                return ""; // Retorna una cadena vacía.
            }
        });

        // GET: Ruta para mostrar el dashboard (panel de control) del usuario.
        // Requiere que el usuario esté autenticado.
        get("/dashboardCarrera", (req, res) -> {
            Map<String, Object> model = new HashMap<>(); // Modelo para la plantilla del dashboard.

            // Intenta obtener el nombre de usuario y la bandera de login de la sesión.
            String currentUsername = req.session().attribute("currentUserUsername");
            Boolean loggedIn = req.session().attribute("loggedIn");

            // 1. Verificar si el usuario ha iniciado sesión.
            // Si no hay un nombre de usuario en la sesión, la bandera es nula o falsa,
            // significa que el usuario no está logueado o su sesión expiró.
            if (currentUsername == null || loggedIn == null || !loggedIn) {
                System.out.println("DEBUG: Acceso no autorizado a /dashboard. Redirigiendo a /login.");
                // Redirige al login con un mensaje de error.
                res.redirect("/login?error=Debes iniciar sesión para acceder a esta página.");
                return null; // Importante retornar null después de una redirección.
            }

            // 2. Si el usuario está logueado, añade el nombre de usuario al modelo para la plantilla.
            model.put("username", currentUsername);

            // 3. Renderiza la plantilla del dashboard con el nombre de usuario.
            return new ModelAndView(model, "dashboard_carrera.mustache");
        }, new MustacheTemplateEngine()); // Especifica el motor de plantillas para esta ruta.

        // GET: Ruta para mostrar el dashboard (panel de control) del usuario.
        // Requiere que el usuario esté autenticado.
        get("/gestionUsuario", (req, res) -> {
            Map<String, Object> model = new HashMap<>(); // Modelo para la plantilla del dashboard.

            String currentUsername = req.session().attribute("username");
            Boolean loggedIn = req.session().attribute("loggedIn");
            String rol = req.session().attribute("rol");

            // Validar sesión
            if (currentUsername == null || loggedIn == null || !loggedIn) {
                res.redirect("/?error=Debes iniciar sesión para acceder a esta página.");
                return null;
            }
            // Validar rol (Solo Admins)
            if (!"administrador".equals(rol)) {
                res.redirect("/dashboard?error=No tienes permisos para gestionar usuarios.");
                return null;
            }
            // Pasamos el nombre de usuario para que el Mustache lo salude
            model.put("username", currentUsername);
            // 3. Renderiza la plantilla del dashboard con el nombre de usuario.
            return new ModelAndView(model, "dashboard_gestUsuario.mustache");
        }, new MustacheTemplateEngine()); // Especifica el motor de plantillas para esta ruta.

       

        get("/reporteRiesgo", (req, res) -> {
            // 1. Verificación de Seguridad
            String rol = req.session().attribute("rol");
            if (!"administrador".equals(rol)) {
                res.redirect("/dashboard?error=Acceso denegado.");
                return null;
            }

            // 2. Consulta SQL corregida según tu schema
            String sqlRiesgo = 
                "SELECT u.nombre, u.apellido, u.dni, u.telefono, e.anioIngreso, " +
                "COUNT(em.materia_codigo) as total_materias, " +
                "SUM(CASE WHEN em.estado IN ('desaprobada', 'libre') THEN 1 ELSE 0 END) as materias_criticas " +
                "FROM estudiante e " +
                "JOIN usuario u ON e.usuario_id = u.id " +
                "LEFT JOIN estudiante_materia em ON e.id = em.estudiante_id " +
                "GROUP BY e.id, u.nombre, u.apellido, u.dni, u.telefono, e.anioIngreso " +
                "HAVING total_materias = 0 OR materias_criticas >= 2 " +
                "ORDER BY materias_criticas DESC";

            List<Map> alumnosCrudos = Base.findAll(sqlRiesgo);
            List<Map<String, Object>> alumnosEnRiesgo = new ArrayList<>();

            for (Map registro : alumnosCrudos) {
                long totalMaterias = ((Number) registro.get("total_materias")).longValue();
                long materiasCriticas = registro.get("materias_criticas") != null ? ((Number) registro.get("materias_criticas")).longValue() : 0;
                
                String tipoRiesgo;
                String colorBadge;

                if (totalMaterias == 0) {
                    tipoRiesgo = "Inactividad (Sin inscripciones)";
                    colorBadge = "bg-orange-100 text-orange-800 border-orange-300";
                } else {
                    tipoRiesgo = "Rendimiento Crítico (" + materiasCriticas + " fallidas)";
                    colorBadge = "bg-red-100 text-red-800 border-red-300";
                }

                registro.put("tipoRiesgo", tipoRiesgo);
                registro.put("colorBadge", colorBadge);
                alumnosEnRiesgo.add(registro);
            }

            Map<String, Object> model = new HashMap<>();
            model.put("alumnosEnRiesgo", alumnosEnRiesgo);
            model.put("totalRiesgo", alumnosEnRiesgo.size());

            return new ModelAndView(model, "reporte_riesgo.mustache");
        }, new MustacheTemplateEngine());

    }
}
