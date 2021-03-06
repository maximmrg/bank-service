package fr.miage.bank.controller;

import fr.miage.bank.assembler.PaiementAssembler;
import fr.miage.bank.entity.Account;
import fr.miage.bank.entity.Carte;
import fr.miage.bank.entity.Paiement;
import fr.miage.bank.input.PaiementInput;
import fr.miage.bank.service.AccountService;
import fr.miage.bank.service.CarteService;
import fr.miage.bank.service.PaiementService;
import fr.miage.bank.validator.PaiementValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.ExposesResourceFor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.transaction.Transactional;
import javax.validation.Valid;
import java.net.URI;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequiredArgsConstructor
@ExposesResourceFor(Paiement.class)
public class PaiementController {

    private final PaiementService paiementService;
    private final CarteService carteService;
    private final AccountService accountService;

    private final PaiementAssembler assembler;
    private final PaiementValidator validator;

    @GetMapping("/users/{userId}/accounts/{accountIban}/cartes/{carteId}/paiements")
    @PreAuthorize("hasPermission(#userId, 'User', 'MANAGE_USER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> getAllPaiementsByCarteId(@PathVariable("userId") String userId, @PathVariable("accountIban") String iban, @PathVariable("carteId") String carteId){
        Iterable<Paiement> allPaiements = paiementService.findAllByCarteId(carteId);
        return ResponseEntity.ok(assembler.toCollectionModel(allPaiements, userId,iban, carteId));
    }

    @GetMapping(value = "/users/{userId}/accounts/{accountIban}/cartes/{carteId}/paiements/{paiementId}")
    @PreAuthorize("hasPermission(#userId, 'User', 'MANAGE_USER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> getOnePaiementById(@PathVariable("userId") String userId, @PathVariable("accountIban") String iban, @PathVariable("carteId") String carteId,
                                                @PathVariable("paiementId") String paiementId){
        return Optional.ofNullable(paiementService.findByIdAndCarteIdAndAccountId(paiementId, carteId, iban)).filter(Optional::isPresent)
                .map(i -> ResponseEntity.ok(assembler.toModel(i.get())))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/paiements")
    @Transactional
    public ResponseEntity<?> createPaiement(@RequestBody @Valid PaiementInput paiement) throws ParseException {

        Optional<Carte> optionalCarte = paiementService.verifyCarte(paiement.getNumCarte(), paiement.getCryptoCarte(), paiement.getNomUser());

        //Si la carte existe, donc que les infos sont correctes
        if(optionalCarte.isPresent()){
            Carte carte = optionalCarte.get();
            Account compteDeb = carte.getAccount();

            if(carte.isBloque() || carte.isDeleted()){
                return ResponseEntity.badRequest().build();
            }

            String paysPaiement = paiement.getPays().toLowerCase();
            String paysCarte = carte.getAccount().getPays().toLowerCase();

            if(carte.isLocalisation()){
                if(paysPaiement != paysCarte){
                    return ResponseEntity.badRequest().build();
                }
            }

            Optional<Account> optionalAccount = accountService.findByIban(paiement.getIbanCrediteur());
            Account compteCred = optionalAccount.get();

            if(carte.isLocalisation()){
                String paysDeb = compteDeb.getPays();
                String paysCred = compteCred.getPays();

                if(paysDeb != paysCred){
                    return ResponseEntity.badRequest().build();
                }
            }

            if(compteDeb.getSolde() >= paiement.getMontant() && carte.getPlafond() >= paiement.getMontant()) {

                Paiement paiement2save = new Paiement(
                        UUID.randomUUID().toString(),
                        carte,
                        paiement.getMontant(),
                        paiement.getPays(),
                        compteCred,
                        paiement.getTaux(),
                        new Timestamp(System.currentTimeMillis()),
                        paiement.getLabel(),
                        paiement.getCateg()
                        );

                Paiement saved = paiementService.createPaiement(paiement2save);
                accountService.debiterAccount(compteDeb, paiement.getMontant());
                accountService.crediterAccount(compteCred, paiement.getMontant(), paiement.getTaux());

                if (carte.isVirtual()) {
                    carteService.deleteVirtualCarte(carte);
                }

                URI location = linkTo(methodOn(PaiementController.class).getOnePaiementById(carte.getAccount().getUser().getId(), carte.getAccount().getIban(), carte.getId(), saved.getId())).toUri();

                return ResponseEntity.created(location).build();
            }
        }

        return ResponseEntity.badRequest().build();
    }
}
