package com.is1.proyecto; // Define el paquete de la aplicación, debe coincidir con la estructura de carpetas.

// Importaciones necesarias para la aplicación Spark
import java.net.URLEncoder; // Utilidad para serializar/deserializar objetos Java a/desde JSON.
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List; // Importa los métodos estáticos principales de Spark (get, post, before, after, etc.).
import java.util.Map;

import org.javalite.activejdbc.Base; // Clase central de ActiveJDBC para gestionar la conexión a la base de datos.
import org.mindrot.jbcrypt.BCrypt; // Utilidad para hashear y verificar contraseñas de forma segura.

import com.fasterxml.jackson.databind.ObjectMapper; // Representa un modelo de datos y el nombre de la vista a renderizar.
import com.is1.proyecto.config.DBConfigSingleton; // Motor de plantillas Mustache para Spark.
import com.is1.proyecto.controllers.AdminController;
import com.is1.proyecto.controllers.CarreraController; // Modelo de ActiveJDBC que representa la tabla 'users'.
import com.is1.proyecto.controllers.EstudianteController; // Modelo de ActiveJDBC que representa la tabla 'profesor'.
import com.is1.proyecto.controllers.MateriaController; // Modelo de ActiveJDBC que representa la tabla 'persona'.
import com.is1.proyecto.controllers.PlanController;
import com.is1.proyecto.controllers.ProfesorController;
import com.is1.proyecto.models.Estudiante;
import com.is1.proyecto.models.EstudianteMateria;
import com.is1.proyecto.models.Materia;
import com.is1.proyecto.models.User;
import com.is1.proyecto.models.Usuario;

import spark.ModelAndView; // Importar todos los controladores
import static spark.Spark.after;
import static spark.Spark.before;
import static spark.Spark.get;
import static spark.Spark.halt;
import static spark.Spark.port;
import static spark.Spark.post;
import spark.template.mustache.MustacheTemplateEngine;



/**
 * Clase principal de la aplicación Spark.
 * Configura las rutas, filtros y el inicio del servidor web.
 */
public class App {

    // Instancia estática y final de ObjectMapper para la serialización/deserialización JSON.
    // Se inicializa una sola vez para ser reutilizada en toda la aplicación.
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Método principal que se ejecuta al iniciar la aplicación.
     * Aquí se configuran todas las rutas y filtros de Spark.
     */
    public static void main(String[] args) {
        port(8080); // Configura el puerto en el que la aplicación Spark escuchará las peticiones (por defecto es 4567).

        // Obtener la instancia única del singleton de configuración de la base de datos.
        DBConfigSingleton dbConfig = DBConfigSingleton.getInstance();

        // --- Filtro 'before' para gestionar la conexión a la base de datos ---
        // Este filtro se ejecuta antes de cada solicitud HTTP.
        before((req, res) -> {
            try {
                // VERIFICACIÓN CLAVE: Solo abre la conexión si no hay una ya abierta en este hilo.
                if (!Base.hasConnection()) {
                    // Abre una conexión a la base de datos utilizando las credenciales del singleton.
                    Base.open(dbConfig.getDriver(), dbConfig.getDbUrl(), dbConfig.getUser(), dbConfig.getPass());
                }
                System.out.println(req.url());

            } catch (Exception e) {
                // Si ocurre un error al abrir la conexión, se registra y se detiene la solicitud
                // con un código de estado 500 (Internal Server Error) y un mensaje JSON.
                System.err.println("Error al abrir conexión con ActiveJDBC: " + e.getMessage());
                halt(500, "{\"error\": \"Error interno del servidor: Fallo al conectar a la base de datos.\"}" + e.getMessage());
            }
        });

        // --- Filtro 'after' para cerrar la conexión a la base de datos ---
        // Este filtro se ejecuta después de que cada solicitud HTTP ha sido procesada.
        after((req, res) -> {
            try {
                // VERIFICACIÓN CLAVE: Solo cierra la conexión si efectivamente hay una abierta.
                if (Base.hasConnection()) {
                    // Cierra la conexión a la base de datos para liberar recursos.
                    Base.close();
                }
            } catch (Exception e) {
                // Si ocurre un error al cerrar la conexión, se registra.
                System.err.println("Error al cerrar conexión con ActiveJDBC: " + e.getMessage());
            }
        });

        // -- Validacion de roles
        before("/inscripcion", (req, res) -> {
            String rol = req.session().attribute("rol");
            if (!"estudiante".equals(rol)) {
                halt(403, "Acceso denegado");
            }
        });

        before("/estadoAcademico", (req, res) -> {
            String rol = req.session().attribute("rol");
            if (!"estudiante".equals(rol)) {
                halt(403, "Acceso denegado");
            }
        });

        before("/perfil", (req, res) -> {
            String rol = req.session().attribute("rol");
            if (!"estudiante".equals(rol)) {
                halt(403, "Acceso denegado");
            }
        });

        before("/inscribir", (req, res) -> {
            String rol = req.session().attribute("rol");
            if (!"estudiante".equals(rol)) {
                halt(403, "Acceso denegado");
            }
        });

        // POST: Maneja el envío del formulario de inicio de sesión.
        post("/login", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            String username = req.queryParams("username");
            String plainTextPassword = req.queryParams("password");
            // Validaciones básicas
            if (username == null || username.isEmpty() || plainTextPassword == null || plainTextPassword.isEmpty()) {
                res.status(400); // Bad Request.
                model.put("errorMessage", "El nombre de usuario y la contraseña son requeridos.");
                return new ModelAndView(model, "login.mustache");
            }
            // 1. Buscamos usando el NUEVO modelo y la columna correcta ('username')
            Usuario ac = Usuario.findFirst("username = ?", username);
            // Si no se encuentra ninguna cuenta con ese nombre de usuario.
            if (ac == null) {
                res.status(401); // Unauthorized.
                model.put("errorMessage", "Usuario o contraseña incorrectos.");
                return new ModelAndView(model, "login.mustache");
            }
            // Usamos el getter que creaste en el paso anterior
            String storedHashedPassword = ac.getPassword();
            // Comparamos las contraseñas
            if (BCrypt.checkpw(plainTextPassword, storedHashedPassword)) {
                res.status(200); // OK.
                // --- 2. Gestión de Sesión Mejorada ---
                req.session(true).attribute("username", ac.getUsername());
                req.session().attribute("usuario_id", ac.getId());
                req.session().attribute("loggedIn", true);
                // ¡LA CLAVE DE LA ARQUITECTURA NUEVA! Guardamos el rol en sesión
                String rol = ac.getRol();
                req.session().attribute("rol", rol);
                System.out.println("DEBUG: Login exitoso para la cuenta: " + username + " | Rol: " + rol);
                // --- 3. Redirección Basada en Roles (Patrón PRG) ---
                // Dependiendo de quién se logueó, lo mandamos a su pantalla correspondiente
                switch (rol) {
                    case "administrador":
                        res.redirect("/dashboard"); // Usamos la ruta que ya tenías armada
                        break;
                    case "profesor":
                        res.redirect("/dashboardProfesor");
                        break;
                    case "estudiante":
                        res.redirect("/dashboardEstudiante");
                        break;
                    default:
                        // Si por algún motivo tiene un rol inválido en la DB
                        res.redirect("/login?error=Rol de usuario no reconocido.");
                }
                return null; // En Spark, después de un redirect SIEMPRE retornamos null
            } else {
                // Contraseña incorrecta.
                res.status(401); // Unauthorized.
                System.out.println("DEBUG: Intento de login fallido para: " + username);
                model.put("errorMessage", "Usuario o contraseña incorrectos.");
                return new ModelAndView(model, "login.mustache");
            }
        }, new MustacheTemplateEngine());

        // POST: Endpoint para añadir usuarios (API que devuelve JSON, no HTML).
        // Advertencia: Esta ruta tiene un propósito diferente a las de formulario HTML.
        post("/add_users", (req, res) -> {
            res.type("application/json"); // Establece el tipo de contenido de la respuesta a JSON.

            // Obtiene los parámetros 'name' y 'password' de la solicitud.
            String name = req.queryParams("name");
            String password = req.queryParams("password");

            // --- Validaciones básicas ---
            if (name == null || name.isEmpty() || password == null || password.isEmpty()) {
                res.status(400); // Bad Request.
                return objectMapper.writeValueAsString(Map.of("error", "Nombre y contraseña son requeridos."));
            }

            try {
                // --- Creación y guardado del usuario usando el modelo ActiveJDBC ---
                User newUser = new User(); // Crea una nueva instancia de tu modelo User.
                // ¡ADVERTENCIA DE SEGURIDAD CRÍTICA!
                // En una aplicación real, las contraseñas DEBEN ser hasheadas (ej. con BCrypt)
                // ANTES de guardarse en la base de datos, NUNCA en texto plano.
                // (Nota: El código original tenía la contraseña en texto plano aquí.
                // Se recomienda usar `BCrypt.hashpw(password, BCrypt.gensalt())` como en la ruta '/user/new').
                newUser.set("name", name); // Asigna el nombre al campo 'name'.
                newUser.set("password", password); // Asigna la contraseña al campo 'password'.
                newUser.saveIt(); // Guarda el nuevo usuario en la tabla 'users'.

                res.status(201); // Created.
                // Devuelve una respuesta JSON con el mensaje y el ID del nuevo usuario.
                return objectMapper.writeValueAsString(Map.of("message", "Usuario '" + name + "' registrado con éxito.", "id", newUser.getId()));

            } catch (Exception e) {
                // Si ocurre cualquier error durante la operación de DB, se captura aquí.
                System.err.println("Error al registrar usuario: " + e.getMessage());
                e.printStackTrace(); // Imprime el stack trace para depuración.
                res.status(500); // Internal Server Error.
                return objectMapper.writeValueAsString(Map.of("error", "Error interno al registrar usuario: " + e.getMessage()));
            }
        });

        registrarRutas();


    } // Fin del método main

    public static void registrarRutas(){
        ProfesorController.registrarRutas();
        EstudianteController.registrarRutas();
        MateriaController.registrarRutas();
        PlanController.registrarRutas();
        CarreraController.registrarRutas();
        AdminController.registrarRutas();
    }
} // Fin de la clase App