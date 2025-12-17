package it.interno.mattinale.chat.ai.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * Genera 30 file JSON (uno per giorno) per il mese di Novembre 2025
 * con struttura identica a questuraIns.json e valori randomici 1..100
 * rispettando i vincoli di coerenza (somme esatte e rapporti <=).
 *
 * Output predefinito: src/main/resources/daily/2025-11/questuraIns-2025-11-XX.json
 */
public class NovemberDataGenerator {

    private static final JsonNodeFactory F = JsonNodeFactory.instance;
    private static final ObjectMapper M = new ObjectMapper();
    private static final DateTimeFormatter IT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static void main(String[] args) throws IOException {
        // Directory di output (sovrascrivibile da args[0])
        String outDir = args != null && args.length > 0
                ? args[0]
                : "src/main/resources/daily/2025-11";

        Path outPath = Paths.get(outDir);
        Files.createDirectories(outPath);

        String province = (args != null && args.length > 1 && !args[1].isBlank()) ? args[1] : "ROMA";

        generateToDirectory(outPath, province);

        System.out.println("[NovemberDataGenerator] Generati 30 file in: " + outPath.toAbsolutePath());
    }

    public static void generateToDirectory(Path outPath, String province) throws IOException {
        Files.createDirectories(outPath);
        for (int day = 1; day <= 30; day++) {
            LocalDate date = LocalDate.of(2025, 11, day);
            ObjectNode root = generateDay(province, date, day);
            String fileName = String.format("questuraIns-2025-11-%02d.json", day);
            Path file = outPath.resolve(fileName);
            M.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), root);
        }
    }

    public static ObjectNode generateDay(String province, LocalDate date, int seedDay) {
        Random rnd = new Random(20251100L + seedDay);
        ObjectNode root = F.objectNode();

        root.set("infoOrganicoView", genInfoOrganico(rnd));
        root.set("fattiDiRilievoView", genFattiDiRilievo(rnd));
        root.set("denunciatiView", gen4PartsWithTotal(rnd, "totali", "italiani", "comunitari", "extraComunitari", "altro"));
        root.set("arrestatiView", gen4PartsWithTotal(rnd, "totali", "italiani", "comunitari", "extraComunitari", "altro"));
        root.set("pattuglieView", genPattuglie(rnd));
        root.set("serviziView", genServizi(rnd));
        root.set("immigrazioneView", genImmigrazione(rnd));
        root.set("controlliAmministrativiQuesturaView", genTriple(rnd,
                "controlliAmministrativi", "illecitiAmministrativi", "provvedimenti"));
        root.set("reatiView", genReati(rnd));
        root.set("misurePrevenzioneView", genMisurePrevenzione(rnd));
        root.set("sequestriQuesturaView", genSequestri(rnd));
        root.set("attiviPrevenzTerritorioQuesturaView", genAttiviPrevenz(rnd, true));
        root.set("attiviPrevenzUfficiInvestigativiQuesturaView", genAttiviPrevenz(rnd, true));
        root.set("attiviPrevenzAltriUfficiQuesturaView", genAttiviPrevenz(rnd, true));
        root.set("attiviPrevenzCrimineView", genAttiviPrevenzCrimine(rnd));

        root.put("dataRiferimento", IT.format(date));
        root.put("datiParziali", rnd.nextBoolean());
        root.put("provincia", province.toUpperCase());
        return root;
    }

    private static ObjectNode genInfoOrganico(Random rnd) {
        ObjectNode n = F.objectNode();
        int organico = r(rnd, 20, 100);
        int forzaPresente = r(rnd, 10, organico);
        int forzaIndisponibile = Math.max(1, organico - forzaPresente - r(rnd, 0, 10));
        int totaleImp = r(rnd, 1, Math.min(100, forzaPresente));
        n.put("organico", organico);
        n.put("forzaPresente", forzaPresente);
        n.put("forzaIndisponibile", forzaIndisponibile);
        n.put("totalePersonaleImpiegatoAttivitaOperativa", totaleImp);
        return n;
    }

    private static ObjectNode genFattiDiRilievo(Random rnd) {
        ObjectNode n = F.objectNode();
        n.put("fattiRilievo", "evento di rilievo " + r(rnd, 1, 100));
        return n;
    }

    private static ObjectNode gen4PartsWithTotal(Random rnd, String totalKey,
                                                 String a, String b, String c, String d) {
        ObjectNode n = F.objectNode();
        int tot = r(rnd, 4, 100);
        int[] parts = partitionSum(rnd, tot, 4);
        n.put(totalKey, tot);
        n.put(a, parts[0]);
        n.put(b, parts[1]);
        n.put(c, parts[2]);
        n.put(d, parts[3]);
        return n;
    }

    private static ObjectNode genPattuglie(Random rnd) {
        ObjectNode n = F.objectNode();

        // Budget totale per pattuglie (6 categorie) per rispettare: totalePattuglie = somma esatta delle categorie
        int budget = r(rnd, 6, 100);
        int[] cats = partitionSum(rnd, budget, 6);
        int totSV = cats[0];
        int totAS = cats[1];
        int totAD = cats[2];
        int repartoPrev = cats[3];
        int altriServ = cats[4];
        int uffInv = cats[5];

        // Squadra Volanti (4 fasce che sommano al proprio totale)
        int[] sv = partitionSum(rnd, totSV, 4);
        n.put("totaleEquipSquadraVolanti", totSV);
        n.put("equipSquadraVolantiMattina", sv[0]);
        n.put("equipSquadraVolantiPomeriggio", sv[1]);
        n.put("equipSquadraVolantiSera", sv[2]);
        n.put("equipSquadraVolantiNotte", sv[3]);

        // Autoradio Sezionali
        int[] as = partitionSum(rnd, totAS, 4);
        n.put("totaleEquipAutoradioSezionali", totAS);
        n.put("equipAutoradioMattinaSezionali", as[0]);
        n.put("equipAutoradioPomeriggioSezionali", as[1]);
        n.put("equipAutoradioSeraSezionali", as[2]);
        n.put("equipAutoradioNotteSezionali", as[3]);

        // Autoradio Distaccati
        int[] ad = partitionSum(rnd, totAD, 4);
        n.put("totaleEquipAutoradioDistaccati", totAD);
        n.put("equipAutoradioMattinaDistaccati", ad[0]);
        n.put("equipAutoradioPomeriggioDistaccati", ad[1]);
        n.put("equipAutoradioSeraDistaccati", ad[2]);
        n.put("equipAutoradioNotteDistaccati", ad[3]);

        // Altri totali
        n.put("totalePattRepartoPrevenzione", repartoPrev);
        n.put("totalePattAltriServizi", altriServ);
        n.put("totalePattUfficiInvestigativi", uffInv);

        // Totale pattuglie = somma esatta delle categorie (<=100)
        n.put("totalePattuglie", budget);

        return n;
    }

    private static ObjectNode genServizi(Random rnd) {
        ObjectNode n = F.objectNode();
        n.put("totaleManifestazioni", r(rnd, 1, 100));
        int terr = r(rnd, 1, 100);
        int rinforzo = r(rnd, 1, Math.min(100 - terr, 100));
        n.put("totalePersTerritoriale", terr);
        n.put("totalePersonaleRinforzo", rinforzo);
        n.put("totalePersImpiegato", safeSum100(terr, rinforzo));
        n.put("totaleServiziAltoImpatto", r(rnd, 1, 100));
        n.put("totaleServiziContrTerr", r(rnd, 1, 100));
        n.put("repartoMobile", r(rnd, 1, 100));
        n.put("battaglione", r(rnd, 1, 100));
        n.put("repartoProntoImpiego", r(rnd, 1, 100));
        return n;
    }

    private static ObjectNode genImmigrazione(Random rnd) {
        ObjectNode n = F.objectNode();
        int cittadini = r(rnd, 3, 100);
        int extra = r(rnd, 1, cittadini); // <= cittadiniStranieri
        n.put("cittadiniStranieri", cittadini);
        n.put("extraComunitari", extra);
        n.put("provvRespi", r(rnd, 1, 100));
        n.put("decretiEspul", r(rnd, 1, 100));
        n.put("decretiAllon", r(rnd, 1, 100));

        int[] rimpatri = partitionSum(rnd, r(rnd, 3, 100), 3);
        n.put("totaleRimpatri", rimpatri[0] + rimpatri[1] + rimpatri[2]);
        n.put("accompagnamento", rimpatri[0]);
        n.put("trattenimento", rimpatri[1]);
        n.put("misuraAlternativa", rimpatri[2]);

        n.put("provvRespAllaFrontiera", r(rnd, 1, 100));
        n.put("ordqu", r(rnd, 1, 100));
        n.put("accompagnamentiCpr", r(rnd, 1, 100));
        return n;
    }

    private static ObjectNode genTriple(Random rnd, String a, String b, String c) {
        ObjectNode n = F.objectNode();
        n.put(a, r(rnd, 1, 100));
        n.put(b, r(rnd, 1, 100));
        n.put(c, r(rnd, 1, 100));
        return n;
    }

    private static ObjectNode genReati(Random rnd) {
        ObjectNode n = F.objectNode();
        int partsCount = 12;
        String[] keys = new String[]{
                "omicidi", "tentatiOmicidi", "lesioni", "violenzeSessuali",
                "rapine", "estorsioni", "usura", "furti",
                "truffe", "danneggiamenti", "stupefacenti"
        };
        // 11 voci; il totale deve essere la somma
        int[] parts = partitionSum(rnd, r(rnd, keys.length, 100), keys.length);
        int sum = 0;
        for (int i = 0; i < keys.length; i++) sum += parts[i];
        n.put("totaleDelitti", sum);
        for (int i = 0; i < keys.length; i++) n.put(keys[i], parts[i]);
        return n;
    }

    private static ObjectNode genMisurePrevenzione(Random rnd) {
        ObjectNode n = F.objectNode();
        String[] keys = new String[]{
                "avvisiOrali", "rimpatri", "daspoEmessi", "daspoFuoriContesto",
                "dacur", "sorveglianze", "ammonimentiEmessi"
        };
        int[] parts = partitionSum(rnd, r(rnd, keys.length, 100), keys.length);
        int sum = 0;
        for (int p : parts) sum += p;
        for (int i = 0; i < keys.length; i++) n.put(keys[i], parts[i]);
        n.put("misurePrevenzione", sum);
        return n;
    }

    private static ObjectNode genSequestri(Random rnd) {
        ObjectNode n = F.objectNode();

        // Budget totale per "numeroSequestri" e ripartizione in 3 macro-aree
        int numeroSequestri = r(rnd, 3, 100);
        int[] macro = partitionSum(rnd, numeroSequestri, 3);
        int sumD = macro[0];
        int sumA = macro[1];
        int sumV = macro[2];

        // Droghe (9 voci) che sommano a sumD
        String[] droghe = new String[]{
                "cocaina", "eroina", "benzodiazepine", "marijuana",
                "fentanyl", "shaboo", "ecstasyMdma", "hashish", "altreDroghe"
        };
        int[] dParts = partitionSum(rnd, Math.max(sumD, droghe.length), droghe.length);
        // Normalizza per sommare esattamente a sumD (se abbiamo forzato minimi)
        dParts = partitionSum(rnd, sumD, droghe.length);
        for (int i = 0; i < droghe.length; i++) n.put(droghe[i], dParts[i]);
        n.put("sequStupefacenti", sumD);

        // Armi/Esplosivi (6 voci) sommano a sumA
        String[] armi = new String[]{
                "armiGuerra", "armiComuni", "armiComuniNonDaSparo",
                "esplosivi", "munizioni", "armi"
        };
        int[] aParts = partitionSum(rnd, sumA, armi.length);
        for (int i = 0; i < armi.length; i++) n.put(armi[i], aParts[i]);
        n.put("sequestriArmiEdEsplosivi", sumA);

        // Veicoli (3 voci) sommano a sumV
        String[] veicoli = new String[]{"autovetture", "motoveicoli", "altreTipologieVetture"};
        int[] vParts = partitionSum(rnd, sumV, veicoli.length);
        for (int i = 0; i < veicoli.length; i++) n.put(veicoli[i], vParts[i]);
        n.put("sequestriVeicoli", sumV);

        // Numero totale sequestri = somma esatta delle macro-aree (<=100)
        n.put("numeroSequestri", numeroSequestri);

        return n;
    }

    private static ObjectNode genAttiviPrevenz(Random rnd, boolean withVehicles) {
        ObjectNode n = F.objectNode();
        int[] cittadini = partitionSum(rnd, r(rnd, 4, 100), 4);
        int persone = cittadini[0] + cittadini[1] + cittadini[2] + cittadini[3];
        n.put("personeControllate", persone);
        n.put("italiani", cittadini[0]);
        n.put("comunitari", cittadini[1]);
        n.put("extraComunitari", cittadini[2]);
        n.put("altro", cittadini[3]);

        int posPers = r(rnd, 1, Math.max(1, persone));
        n.put("positiviPersoneControllate", Math.min(posPers, persone));

        if (withVehicles) {
            int veicoli = r(rnd, 1, 100);
            int posVeic = r(rnd, 1, veicoli);
            n.put("veicoliControllati", veicoli);
            n.put("positiviVeicoli", posVeic);
        }

        int[] modo = partitionSum(rnd, persone, 2);
        n.put("personeControllateIniziativa", modo[0]);
        n.put("personeControllateIntervento", modo[1]);
        return n;
    }

    private static ObjectNode genAttiviPrevenzCrimine(Random rnd) {
        ObjectNode n = F.objectNode();
        int[] cittadini = partitionSum(rnd, r(rnd, 4, 100), 4);
        int persone = cittadini[0] + cittadini[1] + cittadini[2] + cittadini[3];
        n.put("personeControllate", persone);
        n.put("italiani", cittadini[0]);
        n.put("comunitari", cittadini[1]);
        n.put("extraComunitari", cittadini[2]);
        n.put("altro", cittadini[3]);

        int posPers = r(rnd, 1, Math.max(1, persone));
        n.put("positiviPersoneControllate", Math.min(posPers, persone));

        int veicoli = r(rnd, 1, 100);
        int posVeic = r(rnd, 1, veicoli);
        n.put("veicoliControllati", veicoli);
        n.put("positiviVeicoliControllati", posVeic);
        return n;
    }

    // Utilities
    private static int r(Random rnd, int min, int max) {
        if (min < 1) min = 1;
        if (max < min) max = min;
        return rnd.nextInt((max - min) + 1) + min;
    }

    private static int safeSum100(int... vals) {
        int s = 0;
        for (int v : vals) s += v;
        return Math.min(s, 100);
    }

    private static int[] partitionSum(Random rnd, int total, int parts) {
        // Partiziona 'total' in 'parts' interi positivi (>=1) che sommano esattamente a 'total', ciascuno ≤ 100
        int[] res = new int[parts];
        // Genera tagli casuali e normalizza
        int remaining = total;
        for (int i = 0; i < parts - 1; i++) {
            int maxForThis = Math.min(remaining - (parts - i - 1), 100);
            int val = r(rnd, 1, maxForThis);
            res[i] = val;
            remaining -= val;
        }
        res[parts - 1] = remaining;
        // Se l'ultimo supera 100, redistribuisci
        if (res[parts - 1] > 100) {
            int overflow = res[parts - 1] - 100;
            res[parts - 1] = 100;
            int i = 0;
            while (overflow > 0 && i < parts - 1) {
                int canAdd = Math.min(overflow, 100 - res[i]);
                res[i] += canAdd;
                overflow -= canAdd;
                i++;
            }
        }
        return res;
    }
}
