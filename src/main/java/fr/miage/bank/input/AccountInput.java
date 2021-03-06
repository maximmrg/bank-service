package fr.miage.bank.input;

import com.sun.istack.NotNull;

import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import lombok.*;
import org.hibernate.validator.constraints.Length;

import java.util.Date;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccountInput {

    @NotNull
    private String pays;

    @Size(min = 5, max = 10)
    private String secret;

    @Min(0)
    private double solde;

}
