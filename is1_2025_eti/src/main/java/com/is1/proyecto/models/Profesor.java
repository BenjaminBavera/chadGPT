package com.is1.proyecto.models;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Table;

@Table("profesor")
public class Profesor extends Model {

    // --- Dato exclusivo de la tabla Profesor ---
    public String getCorreo() {
        return getString("correo");
    }

    public void setCorreo(String correo) {
        set("correo", correo);
    }

    // --- Clave Foránea que lo une con Usuario ---
    public int getUsuarioId() {
        return getInteger("usuario_id");
    }

    public void setUsuarioId(int usuarioId) {
        set("usuario_id", usuarioId);
    }

}