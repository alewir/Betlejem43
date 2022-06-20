package me.ugeno.betlejem.common.utils.xtb;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class BetlejemXtbConstants {
    /**
     * Max time to wait of WebDriver to web page element to load (seconds) *
     */
    public static final int WAIT_FOR_ELEMENT_TO_LOAD_TIMEOUT = 3;

    public static final String SCREENSHOTS_PATH_PROPERTY = "jif.test.screenshots.path";
    public static final String HUDSON_SCREENSHOTS_LINK = "log.screenshots.link";

    public static final String BETLEJEM_XTB_LOGIN = "xtb.login";
    public static final String BETLEJEM_XTB_PASSWORD = "xtb.password";

    public static final char CSV_SEP = ',';

    public static final String BASE_DATA_PATH = "e:\\ml\\data"; // TODO: Pass it to python scripts

    public static final String DATA_PATH_GPW_D1 = String.format("%s\\gpw_d1", BASE_DATA_PATH);
    public static final String DATA_PATH_GPW_M5 = String.format("%s\\gpw_m5", BASE_DATA_PATH);
    public static final String DATA_PATH_US_D1 = String.format("%s\\us_d1", BASE_DATA_PATH);
    public static final String DATA_PATH_US_M5 = String.format("%s\\us_m5", BASE_DATA_PATH);

    public static final int F_DATA_RETRO_DAYS = 32;
    public static final int F_DATA_RETRO_DAYS_192 = 192;

    public static final String TRAINING_DATA_DATE_FORMAT = "yyyy-MM-dd";
    public static final String TRAINING_DATA_DATE_FORMAT_MIN = "yyyy-MM-dd_HH:mm";

    public static final String VALOR_NAME_ALL = "all";

    public static final String CURRENCY_USD = "USD";
    public static final String CURRENCY_PLN = "PLN";

    public static final double VALUABLE_AVG_PRED_VAL = .90;
    public static final int FRESH_M5_MAX_MINUTES_BACK = 30;
    public static final int DAYS_BACK_TO_FRIDAY = 3;

    public static final int US_MARKET_OPEN_HOUR = 15; // when pending orders can be set
    public static final int US_MARKET_CLOSE_HOUR = 22; // TODO: consider DST

    public static final int GPW_MARKET_OPEN_HOUR = 8; // when pending orders can be set
    public static final int GPW_MARKET_CLOSE_HOUR = 17;

//    static final int PLN_MIN_TOTAL_VAL = 5000; // TODO: adjust depending on commission detection
//    static final int PLN_MAX_TOTAL_VAL = 5500;
//    static final int USD_MIN_TOTAL_VAL = 3000;
//    static final int USD_MAX_TOTAL_VAL = 3500;

    public static final int PLN_MIN_TOTAL_VAL = 500;
    public static final int PLN_MAX_TOTAL_VAL = 550;
    public static final int USD_MIN_TOTAL_VAL = 300;
    public static final int USD_MAX_TOTAL_VAL = 350;

    public static final double MIN_PROFIT_FOR_SELL_STOP = .025;
    public static final double OPPORTUNISTIC_INCREASE_TO_RATIO = 1.16;
    public static final int OPPORTUNISTIC_INCREASE_HOURS = 72;

    public static final String CSV_EXTENSION = "csv";
    public static final String TXT_EXTENSION = "txt";

    public static final BigDecimal ONE_HUNDRED_PROC = new BigDecimal(100);
    public static final BigDecimal ONE_KILO = new BigDecimal(1000);
    public static final BigDecimal BY_HALF = new BigDecimal(2.);
    public static final BigDecimal NEAR_ZERO = new BigDecimal("0.00001");

    public static final int PRICE_SCALE = 2;
    public static final int DIV_SCALE = 4;

    public static final String GPW_URL = "https://www.gpw.pl/archiwum-notowan";
    public static final String DEFAULT_ENC = "UTF-8";

    public static final BigDecimal[] BOUNDARIES_GPW = new BigDecimal[]{
            new BigDecimal("-13.67"),
            new BigDecimal("-9.40"),
            new BigDecimal("-7.67"),
            new BigDecimal("-6.49"),
            new BigDecimal("-5.70"),
            new BigDecimal("-5.10"),
            new BigDecimal("-4.69"),
            new BigDecimal("-4.32"),
            new BigDecimal("-4.00"),
            new BigDecimal("-3.70"),
            new BigDecimal("-3.45"),
            new BigDecimal("-3.23"),
            new BigDecimal("-3.03"),
            new BigDecimal("-2.89"),
            new BigDecimal("-2.73"),
            new BigDecimal("-2.59"),
            new BigDecimal("-2.44"),
            new BigDecimal("-2.30"),
            new BigDecimal("-2.17"),
            new BigDecimal("-2.05"),
            new BigDecimal("-1.97"),
            new BigDecimal("-1.87"),
            new BigDecimal("-1.77"),
            new BigDecimal("-1.67"),
            new BigDecimal("-1.58"),
            new BigDecimal("-1.49"),
            new BigDecimal("-1.40"),
            new BigDecimal("-1.31"),
            new BigDecimal("-1.23"),
            new BigDecimal("-1.15"),
            new BigDecimal("-1.08"),
            new BigDecimal("-1.00"),
            new BigDecimal("-0.93"),
            new BigDecimal("-0.87"),
            new BigDecimal("-0.80"),
            new BigDecimal("-0.74"),
            new BigDecimal("-0.68"),
            new BigDecimal("-0.62"),
            new BigDecimal("-0.56"),
            new BigDecimal("-0.50"),
            new BigDecimal("-0.44"),
            new BigDecimal("-0.38"),
            new BigDecimal("-0.32"),
            new BigDecimal("-0.26"),
            new BigDecimal("-0.19"),
            new BigDecimal("-0.11"),
            BigDecimal.ZERO,
            new BigDecimal("0.14"),
            new BigDecimal("0.23"),
            new BigDecimal("0.31"),
            new BigDecimal("0.39"),
            new BigDecimal("0.46"),
            new BigDecimal("0.53"),
            new BigDecimal("0.61"),
            new BigDecimal("0.69"),
            new BigDecimal("0.76"),
            new BigDecimal("0.83"),
            new BigDecimal("0.91"),
            new BigDecimal("1.01"),
            new BigDecimal("1.11"),
            new BigDecimal("1.21"),
            new BigDecimal("1.31"),
            new BigDecimal("1.41"),
            new BigDecimal("1.51"),
            new BigDecimal("1.61"),
            new BigDecimal("1.71"),
            new BigDecimal("1.83"),
            new BigDecimal("1.95"),
            new BigDecimal("2.03"),
            new BigDecimal("2.16"),
            new BigDecimal("2.31"),
            new BigDecimal("2.45"),
            new BigDecimal("2.61"),
            new BigDecimal("2.79"),
            new BigDecimal("2.95"),
            new BigDecimal("3.11"),
            new BigDecimal("3.32"),
            new BigDecimal("3.55"),
            new BigDecimal("3.81"),
            new BigDecimal("4.06"),
            new BigDecimal("4.38"),
            new BigDecimal("4.75"),
            new BigDecimal("5.12"),
            new BigDecimal("5.66"),
            new BigDecimal("6.31"),
            new BigDecimal("7.18"),
            new BigDecimal("8.51"),
            new BigDecimal("10.01"),
            new BigDecimal("16.70"),
            new BigDecimal(Integer.MAX_VALUE),
    };

    public static final BigDecimal[] BOUNDARIES_US = new BigDecimal[]{
            new BigDecimal("-8.89"),
            new BigDecimal("-6.20"),
            new BigDecimal("-5.00"),
            new BigDecimal("-4.00"),
            new BigDecimal("-3.54"),
            new BigDecimal("-3.00"),
            new BigDecimal("-2.90"),
            new BigDecimal("-2.50"),
            new BigDecimal("-2.27"),
            new BigDecimal("-2.00"),
            new BigDecimal("-1.80"),
            new BigDecimal("-1.60"),
            new BigDecimal("-1.49"),
            new BigDecimal("-1.32"),
            new BigDecimal("-1.20"),
            new BigDecimal("-1.10"),
            new BigDecimal("-1.00"),
            new BigDecimal("-0.90"),
            new BigDecimal("-0.80"),
            new BigDecimal("-0.70"),
            new BigDecimal("-0.60"),
            new BigDecimal("-0.50"),
            new BigDecimal("-0.40"),
            new BigDecimal("-0.30"),
            new BigDecimal("-0.20"),
            new BigDecimal("-0.10"),
            BigDecimal.ZERO,
            new BigDecimal("0.11"),
            new BigDecimal("0.21"),
            new BigDecimal("0.31"),
            new BigDecimal("0.41"),
            new BigDecimal("0.51"),
            new BigDecimal("0.61"),
            new BigDecimal("0.71"),
            new BigDecimal("0.81"),
            new BigDecimal("0.91"),
            new BigDecimal("1.01"),
            new BigDecimal("1.11"),
            new BigDecimal("1.21"),
            new BigDecimal("1.33"),
            new BigDecimal("1.51"),
            new BigDecimal("1.71"),
            new BigDecimal("1.91"),
            new BigDecimal("2.01"),
            new BigDecimal("2.31"),
            new BigDecimal("2.61"),
            new BigDecimal("3.01"),
            new BigDecimal("3.61"),
            new BigDecimal("4.01"),
            new BigDecimal("5.01"),
            new BigDecimal("6.36"),
            new BigDecimal("9.01"),
            new BigDecimal("22.01"),
            new BigDecimal(Integer.MAX_VALUE),
    };

    public static final Map<String, String> gpwMapping = new HashMap<>();

    static {
        gpwMapping.put("11BIT", "11B.PL");
        gpwMapping.put("ABPL", "ABE.PL");
        gpwMapping.put("ACAUTOGAZ", "ACG.PL");
        gpwMapping.put("ACTION", "ACT.PL");
        gpwMapping.put("AGORA", "AGO_9.PL");
        gpwMapping.put("AGROTON", "AGT.PL");
        gpwMapping.put("AILLERON", "ALL.PL");
        gpwMapping.put("AIRWAY", "AWM.PL");
        gpwMapping.put("ALCHEMIA", "ALC.PL");
        gpwMapping.put("ALIOR", "ALR_9.PL");
        gpwMapping.put("ALTUSTFI", "ALI.PL");
        gpwMapping.put("ALUMETAL", "AML.PL");
        gpwMapping.put("AMBRA", "AMB.PL");
        gpwMapping.put("AMICA", "AMC_9.PL");
        gpwMapping.put("AMREST", "EAT_9.PL");
        gpwMapping.put("APATOR", "APT.PL");
        gpwMapping.put("APSENERGY", "APE.PL");
        gpwMapping.put("ARCHICOM", "ARH.PL");
        gpwMapping.put("ARCTIC", "ATC.PL");
        gpwMapping.put("ASBIS", "ASB.PL");
        gpwMapping.put("ASSECOBS", "ABS.PL");
        gpwMapping.put("ASSECOPOL", "ACP_9.PL");
        gpwMapping.put("ASSECOSEE", "ASE.PL");
        gpwMapping.put("ASTARTA", "AST.PL");
        gpwMapping.put("ATAL", "1AT.PL");
        gpwMapping.put("ATENDE", "ATD.PL");
        gpwMapping.put("ATM", "ATG.PL");
        gpwMapping.put("ATMGRUPA", "ATG.PL");
        gpwMapping.put("ATREM", "ATR.PL");
        gpwMapping.put("AUTOPARTN", "APR.PL");
        gpwMapping.put("BAHOLDING", "BAH.PL");
        gpwMapping.put("BBIDEV", "BBD.PL");
        gpwMapping.put("BENEFIT", "BFT_9.PL");
        gpwMapping.put("BIOMEDLUB", "BML.PL");
        gpwMapping.put("BIOTON", "BIO_9.PL");
        gpwMapping.put("BOGDANKA", "LWB_9.PL");
        gpwMapping.put("BORYSZEW", "BRS_9.PL");
        gpwMapping.put("BOS", "BOS.PL");
        gpwMapping.put("BRASTER", "BRA.PL");
        gpwMapping.put("BUDIMEX", "BDX_9.PL");
        gpwMapping.put("SANTANDER", "SPL_9.PL");
        gpwMapping.put("CAPITAL", "CPG.PL");
        gpwMapping.put("CCC", "CCC_9.PL");
        gpwMapping.put("CDPROJEKT", "CDR_9.PL");
        gpwMapping.put("CDRL", "CDL.PL");
        gpwMapping.put("CIECH", "CIE_9.PL");
        gpwMapping.put("CIGAMES", "CIG.PL");
        gpwMapping.put("CLNPHARMA", "CLN.PL");
        gpwMapping.put("COGNOR", "COG.PL");
        gpwMapping.put("COMARCH", "CMR.PL");
        gpwMapping.put("COMP", "CMP.PL");
        gpwMapping.put("CORMAY", "CRM.PL");
        gpwMapping.put("CYFRPLSAT", "CPS_9.PL");
        gpwMapping.put("DEBICA", "DBC.PL");
        gpwMapping.put("DELKO", "DEL.PL");
        gpwMapping.put("DINOPL", "DNP_9.PL");
        gpwMapping.put("DOMDEV", "DOM_9.PL");
        gpwMapping.put("ECHO", "ECH_9.PL");
        gpwMapping.put("EKOEXPORT", "EEX.PL");
        gpwMapping.put("ELBUDOWA", "ELB.PL");
        gpwMapping.put("ELEKTROTI", "ELT.PL");
        gpwMapping.put("ENEA", "ENA_9.PL");
        gpwMapping.put("ENERGA", "ENG_9.PL");
        gpwMapping.put("ENERGOINS", "ENI.PL");
        gpwMapping.put("ENTER", "ASB.PL");
        gpwMapping.put("ERG", "APE.PL");
        gpwMapping.put("ESOTIQ", "EAH.PL");
        gpwMapping.put("EUCO", "EUC.PL");
        gpwMapping.put("EUROCASH", "EUR_9.PL");
        gpwMapping.put("EUROTEL", "ETL.PL");
        gpwMapping.put("FAM", "FMF.PL");
        gpwMapping.put("FAMUR", "FMF.PL");
        gpwMapping.put("FERRO", "FRO.PL");
        gpwMapping.put("FORTE", "FTE_9.PL");
        gpwMapping.put("GETBACK", "GBK.PL");
        gpwMapping.put("GETIN", "GNB_9.PL");
        gpwMapping.put("GETINOBLE", "GNB_9.PL");
        gpwMapping.put("GLCOSMED", "GLC.PL");
        gpwMapping.put("GOBARTO", "GOB.PL");
        gpwMapping.put("GPW", "GPW_9.PL");
        gpwMapping.put("GROCLIN", "GCN.PL");
        gpwMapping.put("GRODNO", "GRN.PL");
        gpwMapping.put("GRUPAAZOTY", "ATT_9.PL");
        gpwMapping.put("GTC", "GTC_9.PL");
        gpwMapping.put("HANDLOWY", "BHW_9.PL");
        gpwMapping.put("HARPER", "HRP.PL");
        gpwMapping.put("HERKULES", "HRS.PL");
        gpwMapping.put("IMCOMPANY", "IMC.PL");
        gpwMapping.put("IMPEL", "IPL.PL");
        gpwMapping.put("IMS", "IMS.PL");
        gpwMapping.put("INC", "INC.PL");
        gpwMapping.put("INGBSK", "ING_9.PL");
        gpwMapping.put("INSTALKRK", "INK.PL");
        gpwMapping.put("INTERAOLT", "IRL.PL");
        gpwMapping.put("INTERCARS", "CAR_9.PL");
        gpwMapping.put("INTROL", "INL.PL");
        gpwMapping.put("IPOPEMA", "IPE.PL");
        gpwMapping.put("IZOBLOK", "IZB.PL");
        gpwMapping.put("JSW", "JSW_9.PL");
        gpwMapping.put("JWCONSTR", "JWC.PL");
        gpwMapping.put("KANIA", "KAN.PL");
        gpwMapping.put("KERNEL", "KER_9.PL");
        gpwMapping.put("KETY", "KTY_9.PL");
        gpwMapping.put("KGHM", "KGH_9.PL");
        gpwMapping.put("KINOPOL", "KPL.PL");
        gpwMapping.put("KOGENERA", "KGN.PL");
        gpwMapping.put("KONSSTALI", "KST.PL");
        gpwMapping.put("KRUK", "KRU_9.PL");
        gpwMapping.put("KRUSZWICA", "KSW.PL");
        gpwMapping.put("KRVITAMIN", "KVT.PL");
        gpwMapping.put("LCCORP", "DVL.PL");
        gpwMapping.put("LENA", "LEN.PL");
        gpwMapping.put("LENTEX", "LTX.PL");
        gpwMapping.put("LIBET", "LBT.PL");
        gpwMapping.put("LIVECHAT", "LVC_9.PL");
        gpwMapping.put("LOKUM", "LKD.PL");
        gpwMapping.put("LOTOS", "LTS_9.PL");
        gpwMapping.put("LPP", "LPP_9.PL");
        gpwMapping.put("LSISOFT", "LSI.PL");
        gpwMapping.put("LUBAWA", "LBW.PL");
        gpwMapping.put("MABION", "MAB.PL");
        gpwMapping.put("MAKARONPL", "MAK.PL");
        gpwMapping.put("MANGATA", "MGT.PL");
        gpwMapping.put("MARVIPOL", "MVP.PL");
        gpwMapping.put("MASTERPHA", "MPH.PL");
        gpwMapping.put("MAXCOM", "MXC.PL");
        gpwMapping.put("MBANK", "MBK_9.PL");
        gpwMapping.put("MCI", "MCI.PL");
        gpwMapping.put("MDIENERGIA", "MDI.PL");
        gpwMapping.put("MEDICALG", "MDG.PL");
        gpwMapping.put("MENNICA", "MNC.PL");
        gpwMapping.put("MERCATOR", "MRC.PL");
        gpwMapping.put("MERCOR", "MCR.PL");
        gpwMapping.put("MEXPOLSKA", "MEX.PL");
        gpwMapping.put("MILLENNIUM", "MIL_9.PL");
        gpwMapping.put("MIRBUD", "MRB.PL");
        gpwMapping.put("MLPGROUP", "MLG.PL");
        gpwMapping.put("MOBRUK", "MBR.PL");
        gpwMapping.put("MOL", "MOL.PL");
        gpwMapping.put("MONNARI", "MON.PL");
        gpwMapping.put("MOSTALWAR", "MSW.PL");
        gpwMapping.put("MWTRADE", "MWT.PL");
        gpwMapping.put("NANOGROUP", "NNG.PL");
        gpwMapping.put("NETIA", "NET_9.PL");
        gpwMapping.put("NEUCA", "NEU.PL");
        gpwMapping.put("NEWAG", "NWG.PL");
        gpwMapping.put("NOVITA", "NVT.PL");
        gpwMapping.put("NTTSYSTEM", "NTT.PL");
        gpwMapping.put("ODLEWNIE", "ODL.PL");
        gpwMapping.put("OEX", "OEX.PL");
        gpwMapping.put("OPONEO", ": OPN.PL");
        gpwMapping.put("OPTEAM", "OPM.PL");
        gpwMapping.put("ORANGEPL", "OPL_9.PL");
        gpwMapping.put("ORZBIALY", "OBL.PL");
        gpwMapping.put("PBG", "PBG_9.PL");
        gpwMapping.put("PBKM", "BKM.PL");
        gpwMapping.put("PCCEXOL", "PCX.PL");
        gpwMapping.put("PCCROKITA", "PCR.PL");
        gpwMapping.put("PEKABEX", "PBX.PL");
        gpwMapping.put("PEKAO", "PEO_9.PL");
        gpwMapping.put("PEP", "PEP.PL");
        gpwMapping.put("PGE", "PGE_9.PL");
        gpwMapping.put("PGNIG", "PGN_9.PL");
        gpwMapping.put("PGSSOFT", "PSW.PL");
        gpwMapping.put("PHN", "PHN.PL");
        gpwMapping.put("PKNORLEN", "PKN_9.PL");
        gpwMapping.put("PKOBP", "PKO_9.PL");
        gpwMapping.put("PKPCARGO", "PKP_9.PL");
        gpwMapping.put("PLAYWAY", "PLW.PL");
        gpwMapping.put("PLAYWAY", "PLW.PL");
        gpwMapping.put("POLICE", "PCE.PL");
        gpwMapping.put("POLIMEXMS", "PXM_9.PL");
        gpwMapping.put("POLNORD", "PND.PL");
        gpwMapping.put("POLWAX", "PWX.PL");
        gpwMapping.put("POZBUD", "POZ.PL");
        gpwMapping.put("PRAGMAFA", "PRF.PL");
        gpwMapping.put("PRAIRIE", "PDZ.PL");
        gpwMapping.put("PROCHEM", "PRM.PL");
        gpwMapping.put("PROTEKTOR", "PRT.PL");
        gpwMapping.put("PULAWY", "ZAP_9.PL");
        gpwMapping.put("PZU", "PZU_9.PL");
        gpwMapping.put("QUERCUS", "QRS.PL");
        gpwMapping.put("R22", "R22.PL");
        gpwMapping.put("RAFAKO", "RFK.PL");
        gpwMapping.put("RAINBOW", "RBW.PL");
        gpwMapping.put("RANKPROGR", "RNK.PL");
        gpwMapping.put("RELPOL", "RLP.PL");
        gpwMapping.put("ROPCZYCE", "RPC.PL");
        gpwMapping.put("RUBICON", "NVV.PL");
        gpwMapping.put("SANOK", "SNK.PL");
        gpwMapping.put("SANTANDER", "SPL_9.PL");
        gpwMapping.put("SANWIL", "SNW.PL");
        gpwMapping.put("SEKO", "SEK.PL");
        gpwMapping.put("SELENAFM", "SEL.PL");
        gpwMapping.put("SELVITA", "SLV.PL");
        gpwMapping.put("SERINUS", "SEN_9.PL");
        gpwMapping.put("SKARBIEC", "SKH.PL");
        gpwMapping.put("SNIEZKA", "SKA.PL");
        gpwMapping.put("STALEXP", "STX.PL");
        gpwMapping.put("STALPROD", "STP.PL");
        gpwMapping.put("STALPROFI", "STF.PL");
        gpwMapping.put("STELMET", "STL.PL");
        gpwMapping.put("SUNEX", "SNX.PL");
        gpwMapping.put("SYGNITY", "SGN.PL");
        gpwMapping.put("SYNEKTIK", "SNT.PL");
        gpwMapping.put("STALEXP", "STX.PL");
        gpwMapping.put("TARCZYNSKI", "TAR.PL");
        gpwMapping.put("TAURONPE", "TPE_9.PL");
        gpwMapping.put("TESGAS", "TSG.PL");
        gpwMapping.put("TIM", "ELT.PL");
        gpwMapping.put("TORPOL", "TOR.PL");
        gpwMapping.put("TOWERINVT", "TOW.PL");
        gpwMapping.put("TOYA", "TOA.PL");
        gpwMapping.put("TRAKCJA", "TRK.PL");
        gpwMapping.put("ULMA", "ULM.PL");
        gpwMapping.put("UNIBEP", "UNI.PL");
        gpwMapping.put("UNIMOT", "UNT.PL");
        gpwMapping.put("URSUS", "URS.PL");
        gpwMapping.put("VIGOSYS", "VGO.PL");
        gpwMapping.put("VINDEXUS", "VIN.PL");
        gpwMapping.put("VISTULA", "VST.PL");
        gpwMapping.put("VIVID", "VVD.PL");
        gpwMapping.put("VOTUM", "VOT.PL");
        gpwMapping.put("VOXEL", "VOX.PL");
        gpwMapping.put("WAWEL", "WWL.PL");
        gpwMapping.put("WIELTON", "WLT.PL");
        gpwMapping.put("WIRTUALNA", "WPL.PL");
        gpwMapping.put("WITTCHEN", "WTN.PL");
        gpwMapping.put("WORKSERV", "WSE.PL");
        gpwMapping.put("XTB", "XTB.PL");
        gpwMapping.put("ZEPAK", "ZEP.PL");
        gpwMapping.put("ZPUE", "PUE.PL");
        gpwMapping.put("ZYWIEC", "ZWC.PL");
    }

    private BetlejemXtbConstants() {
        // Utility class - should not be instantiated
    }
}
