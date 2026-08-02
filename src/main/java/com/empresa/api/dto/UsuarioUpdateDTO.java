package com.empresa.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioUpdateDTO {

    @Size(max = 50, message = "El nombre de usuario no puede exceder los 50 caracteres")
    private String nombreUsuario;

    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    private String password;

    @Email(message = "El formato del email no es válido")
    @Size(max = 150, message = "El email no puede exceder los 150 caracteres")
    private String emailLogin;

    private Boolean activo;
}
