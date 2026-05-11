```mermaid
graph TD
    UI[Vistas HTML / Plantillas Mustache]
    App[App.java - Router Principal e Inicialización]
    
    AdminCtrl[AdminController]
    UserCtrl[Controladores: Estudiante y Profesor]
    AcadCtrl[Controladores: Carrera, Plan, Materia]
    
    ModUsuario[Modelos: Usuario, Estudiante, Profesor]
    ModAcadem[Modelos: Carrera, Plan, Materia]
    ModRel[Modelos Intermedios: EstudianteMateria, Correlatividad]
    
    DB[(Base de Datos SQLite: dev.db)]

    UI -->|Peticiones HTTP GET/POST| App
    
    App -->|Enruta Dashboards y Reportes| AdminCtrl
    App -->|Enruta ABM de Personas| UserCtrl
    App -->|Enruta ABM e Inscripciones| AcadCtrl

    AdminCtrl -->|Consultas Analíticas Base.findAll| DB
    AdminCtrl -->|Usa| ModUsuario
    UserCtrl -->|Validación y Guardado| ModUsuario
    AcadCtrl -->|Gestión Estructural| ModAcadem
    AcadCtrl -->|Verificación de Reglas| ModRel

    ModUsuario -->|Mapeo ActiveJDBC| DB
    ModAcadem -->|Mapeo ActiveJDBC| DB
    ModRel -->|Mapeo ActiveJDBC| DB
    
    AdminCtrl -.->|Renderiza Modelo + Vista| UI
    UserCtrl -.->|Renderiza Modelo + Vista| UI
    AcadCtrl -.->|Renderiza Modelo + Vista| UI
