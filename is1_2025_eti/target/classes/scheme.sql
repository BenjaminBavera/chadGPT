PRAGMA foreign_keys = ON; --Habilita las claves foráneas

-- 1. La superclase que unifica Credenciales + Datos Personales
CREATE TABLE IF NOT EXISTS usuario (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE,      -- Para el login
    password TEXT NOT NULL,             -- Contraseña hasheada
    dni INTEGER NOT NULL UNIQUE,        -- Dato personal
    nombre TEXT NOT NULL,               -- Dato personal
    apellido TEXT NOT NULL,             -- Dato personal
    telefono TEXT,
    rol TEXT CHECK(rol IN ('administrador', 'profesor', 'estudiante')) NOT NULL
);

-- 2. Tabla hija: Profesor
-- Solo contiene datos exclusivos de su rol
CREATE TABLE IF NOT EXISTS profesor (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id INTEGER NOT NULL UNIQUE, -- Relación 1 a 1 con la superclase
    correo TEXT NOT NULL UNIQUE,
    CONSTRAINT fk_profesor_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE CASCADE
);

-- 3. Tabla hija: Estudiante
-- Solo contiene datos exclusivos de su rol
CREATE TABLE IF NOT EXISTS estudiante (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id INTEGER NOT NULL UNIQUE, -- Relación 1 a 1 con la superclase
    anioIngreso INTEGER NOT NULL,
    nivel TEXT CHECK(nivel IN ('principiante', 'avanzado')),
    carrera_id INTEGER,
    CONSTRAINT fk_estudiante_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE CASCADE,
    CONSTRAINT fk_estudiante_carrera FOREIGN KEY (carrera_id) REFERENCES carrera(id) ON DELETE SET NULL
);

-- 1. Tabla Carrera (La entidad padre principal)
CREATE TABLE IF NOT EXISTS carrera (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre TEXT NOT NULL UNIQUE
);

-- 2. Tabla Plan (Pertenece a una Carrera)
CREATE TABLE IF NOT EXISTS plan (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    anio INTEGER NOT NULL, -- ej: 2024
    es_vigente BOOLEAN DEFAULT 1, -- 1 es true, 0 es false en SQLite
    carrera_id INTEGER NOT NULL,
    CONSTRAINT fk_plan_carrera FOREIGN KEY (carrera_id) REFERENCES carrera(id) ON DELETE CASCADE
);

-- 3. Tabla Materia (Pertenece a un Plan)
CREATE TABLE IF NOT EXISTS materia (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre TEXT NOT NULL,
    anio_cursado INTEGER, -- ej: 1 para "Primer Año"
    cuatrimestre INTEGER, -- ej: 1 o 2
    plan_id INTEGER NOT NULL,
    CONSTRAINT fk_materia_plan FOREIGN KEY (plan_id) REFERENCES plan(id) ON DELETE CASCADE
);

-- Tabla correlatividad (relacion recursiva de materia)
CREATE TABLE IF NOT EXISTS correlatividad(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    materia_codigo INTEGER NOT NULL,
    correlativa_codigo INTEGER NOT NULL,
    tipo TEXT CHECK(tipo IN ('regular', 'aprobada')),

    FOREIGN KEY (materia_codigo) REFERENCES materia(id) ON DELETE CASCADE,
    FOREIGN KEY (correlativa_codigo) REFERENCES materia(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS estudiante_materia(
    estudiante_id INTEGER NOT NULL,
    materia_codigo INTEGER NOT NULL,
    estado TEXT DEFAULT 'inscripto',
    nota INTEGER,
    PRIMARY KEY (estudiante_id, materia_codigo),
    FOREIGN KEY (estudiante_id) REFERENCES estudiante(id) ON DELETE CASCADE,
    FOREIGN KEY (materia_codigo) REFERENCES materia(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS profesor_materia(
    profesor_id INTEGER NOT NULL,
    materia_id INTEGER NOT NULL,
    cargo VARCHAR(60) NOT NULL CHECK(cargo IN('Responsable de Cátedra','Jefe Trabajos Prácticos','Ayudante')),
    PRIMARY KEY (profesor_id, materia_id),
    FOREIGN KEY (profesor_id) REFERENCES profesor(id) ON DELETE CASCADE,
    FOREIGN KEY (materia_id) REFERENCES materia(id) ON DELETE CASCADE
);