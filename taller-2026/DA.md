```mermaid
graph TD
    Client[Cliente Web / Navegador]
    Spark[Servidor SparkJava]
    Controllers[Controladores HTTP]
    Mustache[Motor de Plantillas]
    ORM[ActiveJDBC]
    DB[(Base de Datos SQLite)]

    Client -->|1. HTTP Request| Spark
    Spark -->|2. Enruta Petición| Controllers
    Controllers -->|3. Consulta| ORM
    ORM -->|4. Ejecuta SQL| DB
    DB -->|5. Resultados| ORM
    ORM -->|6. Objetos Java| Controllers
    Controllers -->|7. Inyecta Datos| Mustache
    Mustache -->|8. HTML Dinamico| Controllers
    Controllers -->|9. Devuelve| Spark
    Spark -->|10. HTTP Response| Client
