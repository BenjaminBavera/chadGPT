package com.is1.proyecto.controllers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.javalite.activejdbc.Base;
import org.mindrot.jbcrypt.BCrypt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.is1.proyecto.models.EstudianteMateria;
import com.is1.proyecto.models.Materia;
import com.is1.proyecto.models.Profesor;
import com.is1.proyecto.models.ProfesorMateria;
import com.is1.proyecto.models.Usuario;

import spark.Filter;
import spark.ModelAndView;
import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.halt;
import static spark.Spark.before;
import spark.template.mustache.MustacheTemplateEngine;

public class ProfesorController {

    // Instancia estática y final de ObjectMapper para la serialización/deserialización JSON.
    // Se inicializa una sola vez para ser reutilizada en toda la aplicación.
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void registrarRutas(){

        //Definición del filtro de seguridad.

        // A. Filtro Exclusivo para Administradores
        Filter filtroAdmin = (req, res) -> {
            Boolean loggedIn = req.session().attribute("loggedIn");
            Boolean esAdmin = req.session().attribute("esAdmin");

            if (loggedIn == null || !loggedIn) {
                res.redirect("/login?error=" + URLEncoder.encode("Debes iniciar sesión primero.", StandardCharsets.UTF_8));
                halt();
            } else if (esAdmin == null || !esAdmin) { // <-- Evaluamos el booleano
                res.redirect("/login?error=" + URLEncoder.encode("Acceso denegado. Solo administradores.", StandardCharsets.UTF_8));
                halt();
            }
        };

        // B. Filtro Exclusivo para Profesores
        Filter filtroProfesor = (req, res) -> {
            Boolean loggedIn = req.session().attribute("loggedIn");
            Boolean esProfesor = req.session().attribute("esProfesor");

            if (loggedIn == null || !loggedIn) {
                res.redirect("/login?error=" + URLEncoder.encode("Debes iniciar sesión primero.", StandardCharsets.UTF_8));
                halt();
            } else if (esProfesor == null || !esProfesor) {
                res.redirect("/dashboard?error=" + URLEncoder.encode("Acceso denegado. Área exclusiva para profesores.", StandardCharsets.UTF_8));
                halt();
            }
        };
        
        //Aplicación del filtro a las rutas.

        // Rutas de Administración 
        before("/registrarProfesor", filtroAdmin);
        before("/registrarProfesor/new", filtroAdmin);
        before("/vincularProfesores", filtroAdmin);
        
        // Rutas de de Profesor
        before("/dashboardProfesor", filtroProfesor);
        before("/perfilProfesor", filtroProfesor);
        before("/cambiarPasswordProfesor", filtroProfesor);
        before("/materiasAsignadas", filtroProfesor);
        before("/cargarNotas/*", filtroProfesor);
        before("/cargarNotas", filtroProfesor);

        get("/registrarProfesor", (req, res) -> {
            Map<String, Object> model = new HashMap<>(); // Crea un mapa para pasar datos a la plantilla.
            String currentUsername = req.session().attribute("username");
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
            // Renderiza la plantilla 'registrarProfesor.mustache' con los datos del modelo.
            return new ModelAndView(model, "registrarProfesor.mustache");
        }, new MustacheTemplateEngine()); // Especifica el motor de plantillas para esta ruta.


        // POST: Maneja el envío del formulario de creación de un profesor nuevo.
        post("/registrarProfesor/new", (req, res) -> {
            // 1. Ahora necesitamos credenciales de acceso, además de los datos físicos
            String username = req.queryParams("username");
            String password = req.queryParams("password");
            String nombre = req.queryParams("nombre");
            String apellido = req.queryParams("apellido");
            String correo = req.queryParams("correo");
            String dni = req.queryParams("dni");
            String telefono = req.queryParams("telefono"); // Agregamos teléfono que está en la DB

            // Validaciones básicas (Asegurate de que no falten los nuevos campos)
            if (username == null || username.isEmpty() || password == null || password.isEmpty() ||
                    nombre == null || nombre.isEmpty() || apellido == null || apellido.isEmpty() ||
                    correo == null || correo.isEmpty() || dni == null || dni.isEmpty() ){

                res.status(400);
                res.redirect("/registrarProfesor?error=Todos los campos obligatorios son requeridos.");
                return "";
            }

            // Validar formato de correo (Mantenemos tu lógica)
            if (!correo.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
                res.redirect("/registrarProfesor?error=Correo invalido.");
                return "";
            }

            try {
                Base.openTransaction();
                // 1. Buscamos si la persona ya existe físicamente (por DNI)
                Usuario user = Usuario.findFirst("dni = ?", dni);

                if (user != null) {
                    // La persona existe. Verificamos que no sea profesor ya.
                    if (Profesor.findFirst("usuario_id = ?", user.getId()) != null) {
                        throw new Exception("Esta persona ya está registrada como profesor en el sistema.");
                    }
                    // Validamos que el correo no lo esté usando otro profesor
                    if (Profesor.findFirst("correo = ?", correo) != null) {
                        throw new Exception("El correo ya está registrado en otro perfil de profesor.");
                    }
                } else {
                    // La persona NO existe. Validamos credenciales únicas.
                    if (Usuario.findFirst("username = ?", username) != null) {
                        throw new Exception("El nombre de usuario ya está en uso.");
                    }
                    if (Profesor.findFirst("correo = ?", correo) != null) {
                        throw new Exception("El correo ya está registrado.");
                    }

                    // Creamos el Padre (Usuario) desde cero
                    user = new Usuario();
                    user.setUsername(username);
                    user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt())); 
                    user.setName(nombre);
                    user.setApellido(apellido);
                    user.setDNI(Integer.parseInt(dni));
                    user.setTelefono(telefono); 
                    user.setRol("profesor"); // Rol base
                    user.saveIt();
                }
                // 2. Creamos el hijo (Profesor) y lo vinculamos
                Profesor pro = new Profesor();
                pro.set("usuario_id", user.getId()); 
                pro.set("correo", correo);
                pro.saveIt();
                Base.commitTransaction();
                res.status(201);
                String mensaje = "Profesor " + nombre + " registrado exitosamente!";
                res.redirect("/registrarProfesor?message=" + URLEncoder.encode(mensaje, StandardCharsets.UTF_8));
                return "";

            } catch (Exception e) {
                // SI ALGO FALLA, DESHACEMOS TODO
                Base.rollbackTransaction();
                System.err.println("Error al registrar el profesor: " + e.getMessage());
                e.printStackTrace();

                // Pasamos el mensaje de la excepción para que el usuario sepa qué falló (ej: "El DNI ya está registrado")
                String errorMsg = URLEncoder.encode("Error: " + e.getMessage(), StandardCharsets.UTF_8.toString());
                res.redirect("/registrarProfesor?error=" + errorMsg);
                return "";
            }
        });
        // POST: Endpoint para añadir profesores (API que devuelve JSON, no HTML).
        // Advertencia: Esta ruta tiene un propósito diferente a las de formulario HTML.
        post("/add_profesor", (req, res) -> {
            res.type("application/json"); // Establece el tipo de contenido de la respuesta a JSON.

            // Obtiene los parámetros 'name' y 'password' de la solicitud.
            String nombre = req.queryParams("nombre");
            String apellido = req.queryParams("apellido");
            String correo = req.queryParams("correo");
            String dni = req.queryParams("dni");

            // --- Validaciones de nombre y apellido ---
            if (nombre == null || nombre.isEmpty() || apellido == null || apellido.isEmpty()) {
                res.status(400); // Bad Request.
                return objectMapper.writeValueAsString(Map.of("error", "Nombre y apellido son requeridos."));
            }
            // --- Validacion de correo---
            if (correo == null || correo.isEmpty() || !correo.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
                res.status(400); // Bad Request.
                return objectMapper.writeValueAsString(Map.of("error", "Correo invalido."));
            }

            try {
                // --- Creación y guardado del usuario usando el modelo ActiveJDBC ---
                Profesor newProfesor = new Profesor(); // Crea una nueva instancia de tu modelo User.

                newProfesor.set("nombre", nombre); // Asigna el nombre al campo 'nombre'.
                newProfesor.set("apellido", apellido); // Asigna la contraseña al campo 'apellido'.
                newProfesor.set("correo", correo);
                newProfesor.set("dni", dni);
                newProfesor.saveIt(); // Guarda el nuevo usuario en la tabla 'profesor'.

                res.status(201); // Created.
                // Devuelve una respuesta JSON con el mensaje y el ID del nuevo usuario.
                return objectMapper.writeValueAsString(Map.of("message", "Profesor '" + nombre + "' registrado con éxito."));

            } catch (Exception e) {
                // Si ocurre cualquier error durante la operación de DB, se captura aquí.
                System.err.println("Error al registrar profesor: " + e.getMessage());
                e.printStackTrace(); // Imprime el stack trace para depuración.
                res.status(500); // Internal Server Error.
                return objectMapper.writeValueAsString(Map.of("error", "Error interno al registrar profesor: " + e.getMessage()));
            }
        });

        // GET: Mostrar el formulario para vincular profesores a materias
        get("/vincularProfesores", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            // Atajar mensajes de éxito o error
            String successMessage = req.queryParams("successMessage");
            if (successMessage != null) model.put("successMessage", successMessage);
            String errorMessage = req.queryParams("errorMessage");
            if (errorMessage != null) model.put("errorMessage", errorMessage);

            // PASO CLAVE: Buscar datos para llenar los dropdowns
            List<Materia> listaMaterias = Materia.findAll();
            // Buscamos en la tabla Usuario a los que tienen rol 'profesor'
            List<Usuario> listaProfesores = Usuario.where("rol = ?", "profesor");

            model.put("materias", listaMaterias);
            model.put("profesores", listaProfesores);

            return new ModelAndView(model, "vincular_profesores.mustache");
        }, new MustacheTemplateEngine());

        // POST: Procesar la vinculación
        post("/vincularProfesores", (req, res) -> {
            try {
                // 1. Obtener datos del formulario
                // Nota: El form nos manda el ID del Usuario, no el del Profesor directamente
                String usuarioIdStr = req.queryParams("profesor_id");
                String materiaIdStr = req.queryParams("materia_id");
                String cargo = req.queryParams("cargo");

                // Validar nulos
                if (usuarioIdStr == null || materiaIdStr == null || cargo == null) {
                    String msg = URLEncoder.encode("Todos los campos son obligatorios.", StandardCharsets.UTF_8.toString());
                    res.redirect("/vincularProfesores?errorMessage=" + msg);
                    return null;
                }

                int usuarioId = Integer.parseInt(usuarioIdStr);
                int materiaId = Integer.parseInt(materiaIdStr);

                // 2. Buscar el registro "hijo" (Profesor) asociado a ese Usuario
                Profesor profReal = Profesor.findFirst("usuario_id = ?", usuarioId);

                if (profReal == null) {
                    String msg = URLEncoder.encode("Error interno: No se encontró el registro físico del profesor.", StandardCharsets.UTF_8.toString());
                    res.redirect("/vincularProfesores?errorMessage=" + msg);
                    return null;
                }

                // 3. Crear y guardar la relación en la base de datos
                ProfesorMateria vinculo = new ProfesorMateria();
                vinculo.set("profesor_id", profReal.getId());
                vinculo.set("materia_id", materiaId);
                vinculo.set("cargo", cargo);
                vinculo.saveIt(); // Si ya está vinculado, ActiveJDBC tirará DBException (por la primary key compuesta)

                // 4. Redirigir con éxito
                String successMsg = URLEncoder.encode("Profesor vinculado a la materia exitosamente.", StandardCharsets.UTF_8.toString());
                res.redirect("/vincularProfesores?successMessage=" + successMsg);

            } catch (org.javalite.activejdbc.DBException e) {
                System.err.println("Error de BD: " + e.getMessage());
                res.redirect("/vincularProfesores?errorMessage=Este profesor ya está vinculado a esta materia.");
            } catch (Exception e) {
                System.err.println("Error general: " + e.getMessage());
                res.redirect("/vincularProfesores?errorMessage=Error interno al procesar la solicitud.");
            }
            return null;
        });

        get("/dashboardProfesor", (req, res)->{
            Map<String, Object> model = new HashMap<>();
            String currentUsername = req.session().attribute("currentUsername");
            model.put("username", currentUsername);
            return new ModelAndView(model, "dashboard_profesor.mustache");
        }, new MustacheTemplateEngine());

        get("/perfilProfesor", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            Integer usuarioId = req.session().attribute("usuario_id");
            Usuario user = Usuario.findById(usuarioId);
            
            // Transformamos el objeto de ActiveJDBC a un Mapa limpio para Mustache
            Map<String, Object> usuarioData = new HashMap<>();
            usuarioData.put("username", user.getString("username"));
            usuarioData.put("name", user.getString("nombre")); 
            usuarioData.put("apellido", user.getString("apellido"));
            usuarioData.put("dni", user.getString("dni"));

            model.put("usuario", usuarioData);

            String message = req.queryParams("message");
            String error = req.queryParams("error");
            if (message != null) model.put("message", message);
            if (error != null) model.put("error", error);
            return new ModelAndView(model, "perfil_profesor.mustache");
        }, new MustacheTemplateEngine());

        post("/cambiarPasswordProfesor", (req, res) -> {
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

        get("/materiasAsignadas", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            String currentUsername = req.session().attribute("username");

            try {
                // 1. Buscar quién es el profesor que está logueado
                Usuario userLogueado = Usuario.findFirst("username = ?", currentUsername);
                Profesor profFisico = Profesor.findFirst("usuario_id = ?", userLogueado.getId());

                if (profFisico != null) {
                    // 2. La consulta mágica (JOIN) para traer todos los datos cruzados
                    // Usamos LEFT JOIN para los planes por si una materia aún no fue vinculada a un plan
                    String sql = "SELECT m.id, m.nombre AS materia_nombre, pm.cargo, p.anio AS plan_anio " +
                            "FROM profesor_materia pm " +
                            "JOIN materia m ON pm.materia_id = m.id " +
                            "JOIN plan p ON m.plan_id = p.id " +
                            "WHERE pm.profesor_id = ?";

                    List<Map> listaMaterias = Base.findAll(sql, profFisico.getId());
                    model.put("materiasAsignadas", listaMaterias);
                }
            } catch (Exception e) {
                System.err.println("Error al cargar materias del profesor: " + e.getMessage());
                model.put("errorMessage", "Error interno al cargar las materias.");
            }
            return new ModelAndView(model, "materias_profesor.mustache");
        }, new MustacheTemplateEngine());

        get("/cargarNotas/:materiaId", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            int materiaId = Integer.parseInt(req.params("materiaId"));

            List<Map> estudiantes = Base.findAll(
                "SELECT u.nombre, u.apellido, u.dni, em.estado, em.nota, em.estudiante_id, em.materia_codigo " +
                "FROM estudiante_materia em " +
                "JOIN estudiante e ON em.estudiante_id = e.id " +
                "JOIN usuario u ON e.usuario_id = u.id " +
                "WHERE em.materia_codigo = ?",
                materiaId
            );

            Materia materia = Materia.findById(materiaId);
            model.put("estudiantes", estudiantes);
            model.put("materia", materia);

            String error = req.queryParams("error");
            if (error != null) model.put("error", error);
            String message = req.queryParams("message");
            if (message != null) model.put("message", message);

            return new ModelAndView(model, "cargar_notas.mustache");
        }, new MustacheTemplateEngine());

        post("/cargarNotas", (req, res) -> {
            int estudianteId = Integer.parseInt(req.queryParams("estudiante_id"));
            int materiaId = Integer.parseInt(req.queryParams("materia_id"));
            String notaStr = req.queryParams("nota");
            String estadoManual = req.queryParams("estado_manual");

            try {
                EstudianteMateria em = EstudianteMateria.findFirst(
                    "estudiante_id = ? AND materia_codigo = ?", estudianteId, materiaId
                );

                if (em == null) {
                    String err = URLEncoder.encode("No se encontro la inscripcion.", StandardCharsets.UTF_8.toString());
                    res.redirect("/cargarNotas/" + materiaId + "?error=" + err);
                    return null;
                }

                // Si el docente cambia manualmente a aprobada
                if (estadoManual != null && estadoManual.equals("aprobada")) {
                    Base.exec(
                        "UPDATE estudiante_materia SET estado = ? WHERE estudiante_id = ? AND materia_codigo = ?",
                        "aprobada", estudianteId, materiaId
                    );
                    String msg = URLEncoder.encode("Estado actualizado a aprobada.", StandardCharsets.UTF_8.toString());
                    res.redirect("/cargarNotas/" + materiaId + "?message=" + msg);
                    return null;
                }

                // Calcular estado según nota
                int nota = Integer.parseInt(notaStr);
                if (nota < 1 || nota > 10) {
                    String err = URLEncoder.encode("La nota debe estar entre 1 y 10.", StandardCharsets.UTF_8.toString());
                    res.redirect("/cargarNotas/" + materiaId + "?error=" + err);
                    return null;
                }

                String estado;
                if (nota >= 7) {
                    estado = "aprobada";
                } else if (nota >= 5) {
                    estado = "regular";
                } else {
                    estado = "libre";
                }

                Base.exec(
                    "UPDATE estudiante_materia SET nota = ?, estado = ? WHERE estudiante_id = ? AND materia_codigo = ?",
                    nota, estado, estudianteId, materiaId
                );

                String msg = URLEncoder.encode("Nota guardada correctamente.", StandardCharsets.UTF_8.toString());
                res.redirect("/cargarNotas/" + materiaId + "?message=" + msg);

            } catch (Exception e) {
                System.err.println("Error al cargar nota: " + e.getMessage());
                String err = URLEncoder.encode("Error al guardar la nota.", StandardCharsets.UTF_8.toString());
                res.redirect("/cargarNotas/" + materiaId + "?error=" + err);
            }
            return null;
        });
    }
}
