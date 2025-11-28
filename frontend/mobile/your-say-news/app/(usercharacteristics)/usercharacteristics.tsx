
import React, { useState } from "react";
import {
    View,
    Text,
    TextInput,
    TouchableOpacity,
    ScrollView,
    StyleSheet,
    Alert,
    Modal,
    Pressable,
    Animated,
} from "react-native";

import { useRouter } from "expo-router";
import { SelectableChip } from "../components/SelectableChip";
import { useAuth } from "../contexts/AuthContext";
import { LinearGradient } from "expo-linear-gradient";


type Option = {
    label: string;
    value: string; // MUST match your Java enum name exactly
};

/* (non-enum) */

const AGE_RANGES: string[] = [
    "13–17",
    "18–24",
    "25–34",
    "35–44",
    "45–54",
    "55–64",
    "65+",
    "Prefer not to say",
];

const GENDER_OPTIONS: string[] = [
    "Woman",
    "Man",
    "Non-binary / gender diverse",
    "Prefer not to say",
    "Prefer to self-describe",
];

const EDUCATION_OPTIONS: string[] = [
    "No formal education",
    "High school or equivalent",
    "Some college / vocational",
    "Bachelor’s or equivalent",
    "Master’s or equivalent",
    "Doctorate",
    "Prefer not to say",
];

const OCCUPATION_OPTIONS: string[] = [
    "Student",
    "Employed full-time",
    "Employed part-time",
    "Self-employed",
    "Unemployed",
    "Retired",
    "Prefer not to say",
];

const NEWS_FREQUENCY: string[] = [
    "Never",
    "Once a week",
    "A few times a week",
    "Once a day",
    "Multiple times a day",
];




const RACE_OPTIONS: Option[] = [
    { label: "Asian", value: "ASIAN" },
    { label: "Black", value: "BLACK" },
    { label: "White", value: "WHITE" },
    { label: "Hispanic", value: "HISPANIC" },
    { label: "Native American", value: "NATIVE_AMERICAN" },
    { label: "Pacific Islander", value: "PACIFIC_ISLANDER" },
    { label: "Other", value: "OTHER" },
];


const SEX_AT_BIRTH_OPTIONS: Option[] = [
    { label: "Male", value: "MALE" },
    { label: "Female", value: "FEMALE" },
];


const HEIGHT_OPTIONS: Option[] = [
    { label: `4'0" – 4'4"`, value: "FEET_4_0_TO_4_4" },
    { label: `4'5" – 4'9"`, value: "FEET_4_5_TO_4_9" },
    { label: `5'0" – 5'3"`, value: "FEET_5_0_TO_5_3" },
    { label: `5'4" – 5'6"`, value: "FEET_5_4_TO_5_6" },
    { label: `5'7" – 5'9"`, value: "FEET_5_7_TO_5_9" },
    { label: `5'10" – 6'0"`, value: "FEET_5_10_TO_6_0" },
    { label: `6'1" – 6'3"`, value: "FEET_6_1_TO_6_3" },
    { label: `6'4" – 6'6"`, value: "FEET_6_4_TO_6_6" },
    { label: `6'7" – 7'0"`, value: "FEET_6_7_TO_7_0" },
    { label: `7'1"+`, value: "FEET_7_1_PLUS" },
];


const WEIGHT_OPTIONS: Option[] = [
    { label: "30–39 kg", value: "KG_30_39" },
    { label: "40–49 kg", value: "KG_40_49" },
    { label: "50–59 kg", value: "KG_50_59" },
    { label: "60–69 kg", value: "KG_60_69" },
    { label: "70–79 kg", value: "KG_70_79" },
    { label: "80–89 kg", value: "KG_80_89" },
    { label: "90–99 kg", value: "KG_90_99" },
    { label: "100–109 kg", value: "KG_100_109" },
    { label: "110–119 kg", value: "KG_110_119" },
    { label: "120–129 kg", value: "KG_120_129" },
    { label: "130–139 kg", value: "KG_130_139" },
    { label: "140–149 kg", value: "KG_140_149" },
    { label: "150+ kg", value: "KG_150_PLUS" },
];


const INCOME_OPTIONS: Option[] = [
    { label: "Below 20k", value: "BELOW_20K" },
    { label: "20k–50k", value: "BETWEEN_20K_AND_50K" },
    { label: "50k–100k", value: "BETWEEN_50K_AND_100K" },
    { label: "100k–200k", value: "BETWEEN_100K_AND_200K" },
    { label: "200k–500k", value: "BETWEEN_200K_AND_500K" },
    { label: "500k–1M", value: "BETWEEN_500K_AND_1000K" },
    { label: "Above 1M", value: "ABOVE_1000000" }, // adjust to your enum value if different
];


const PARENT_OPTIONS: Option[] = [
    { label: "Mum", value: "MUM" },
    { label: "Dad", value: "DAD" },
    { label: "No", value: "NO" },
];


const EYE_COLOR_OPTIONS: Option[] = [
    { label: "Brown", value: "BROWN" },
    { label: "Blue", value: "BLUE" },
    { label: "Green", value: "GREEN" },
    { label: "Hazel", value: "HAZEL" },
    { label: "Gray", value: "GRAY" },
];

const COUNTRY_OF_BIRTH_OPTIONS: Option[] = [
    { label: "Afghanistan", value: "AFGHANISTAN" },
    { label: "Albania", value: "ALBANIA" },
    { label: "Algeria", value: "ALGERIA" },
    { label: "Andorra", value: "ANDORRA" },
    { label: "Angola", value: "ANGOLA" },
    { label: "Antigua and Barbuda", value: "ANTIGUA_AND_BARBUDA" },
    { label: "Argentina", value: "ARGENTINA" },
    { label: "Armenia", value: "ARMENIA" },
    { label: "Australia", value: "AUSTRALIA" },
    { label: "Austria", value: "AUSTRIA" },
    { label: "Azerbaijan", value: "AZERBAIJAN" },

    { label: "Bahamas", value: "BAHAMAS" },
    { label: "Bahrain", value: "BAHRAIN" },
    { label: "Bangladesh", value: "BANGLADESH" },
    { label: "Barbados", value: "BARBADOS" },
    { label: "Belarus", value: "BELARUS" },
    { label: "Belgium", value: "BELGIUM" },
    { label: "Belize", value: "BELIZE" },
    { label: "Benin", value: "BENIN" },
    { label: "Bhutan", value: "BHUTAN" },
    { label: "Bolivia", value: "BOLIVIA" },
    { label: "Bosnia and Herzegovina", value: "BOSNIA_AND_HERZEGOVINA" },
    { label: "Botswana", value: "BOTSWANA" },
    { label: "Brazil", value: "BRAZIL" },
    { label: "Brunei", value: "BRUNEI" },
    { label: "Bulgaria", value: "BULGARIA" },
    { label: "Burkina Faso", value: "BURKINA_FASO" },
    { label: "Burundi", value: "BURUNDI" },

    { label: "Cabo Verde", value: "CABO_VERDE" },
    { label: "Cambodia", value: "CAMBODIA" },
    { label: "Cameroon", value: "CAMEROON" },
    { label: "Canada", value: "CANADA" },
    { label: "Central African Republic", value: "CENTRAL_AFRICAN_REPUBLIC" },
    { label: "Chad", value: "CHAD" },
    { label: "Chile", value: "CHILE" },
    { label: "China", value: "CHINA" },
    { label: "Colombia", value: "COLOMBIA" },
    { label: "Comoros", value: "COMOROS" },
    { label: "Congo, Democratic Republic of", value: "CONGO_DEMOCRATIC_REPUBLIC" },
    { label: "Congo, Republic of", value: "CONGO_REPUBLIC" },
    { label: "Costa Rica", value: "COSTA_RICA" },
    { label: "Côte d’Ivoire", value: "COTE_DIVOIRE" },
    { label: "Croatia", value: "CROATIA" },
    { label: "Cuba", value: "CUBA" },
    { label: "Cyprus", value: "CYPRUS" },
    { label: "Czech Republic", value: "CZECH_REPUBLIC" },

    { label: "Denmark", value: "DENMARK" },
    { label: "Djibouti", value: "DJIBOUTI" },
    { label: "Dominica", value: "DOMINICA" },
    { label: "Dominican Republic", value: "DOMINICAN_REPUBLIC" },

    { label: "Ecuador", value: "ECUADOR" },
    { label: "Egypt", value: "EGYPT" },
    { label: "El Salvador", value: "EL_SALVADOR" },
    { label: "Equatorial Guinea", value: "EQUATORIAL_GUINEA" },
    { label: "Eritrea", value: "ERITREA" },
    { label: "Estonia", value: "ESTONIA" },
    { label: "Eswatini", value: "ESWATINI" },
    { label: "Ethiopia", value: "ETHIOPIA" },

    { label: "Fiji", value: "FIJI" },
    { label: "Finland", value: "FINLAND" },
    { label: "France", value: "FRANCE" },

    { label: "Gabon", value: "GABON" },
    { label: "Gambia", value: "GAMBIA" },
    { label: "Georgia", value: "GEORGIA" },
    { label: "Germany", value: "GERMANY" },
    { label: "Ghana", value: "GHANA" },
    { label: "Greece", value: "GREECE" },
    { label: "Grenada", value: "GRENADA" },
    { label: "Guatemala", value: "GUATEMALA" },
    { label: "Guinea", value: "GUINEA" },
    { label: "Guinea-Bissau", value: "GUINEA_BISSAU" },
    { label: "Guyana", value: "GUYANA" },

    { label: "Haiti", value: "HAITI" },
    { label: "Honduras", value: "HONDURAS" },
    { label: "Hungary", value: "HUNGARY" },

    { label: "Iceland", value: "ICELAND" },
    { label: "India", value: "INDIA" },
    { label: "Indonesia", value: "INDONESIA" },
    { label: "Iran", value: "IRAN" },
    { label: "Iraq", value: "IRAQ" },
    { label: "Ireland", value: "IRELAND" },
    { label: "Israel", value: "ISRAEL" },
    { label: "Italy", value: "ITALY" },

    { label: "Jamaica", value: "JAMAICA" },
    { label: "Japan", value: "JAPAN" },
    { label: "Jordan", value: "JORDAN" },

    { label: "Kazakhstan", value: "KAZAKHSTAN" },
    { label: "Kenya", value: "KENYA" },
    { label: "Kiribati", value: "KIRIBATI" },
    { label: "Kuwait", value: "KUWAIT" },
    { label: "Kyrgyzstan", value: "KYRGYZSTAN" },

    { label: "Laos", value: "LAOS" },
    { label: "Latvia", value: "LATVIA" },
    { label: "Lebanon", value: "LEBANON" },
    { label: "Lesotho", value: "LESOTHO" },
    { label: "Liberia", value: "LIBERIA" },
    { label: "Libya", value: "LIBYA" },
    { label: "Liechtenstein", value: "LIECHTENSTEIN" },
    { label: "Lithuania", value: "LITHUANIA" },
    { label: "Luxembourg", value: "LUXEMBOURG" },

    { label: "Madagascar", value: "MADAGASCAR" },
    { label: "Malawi", value: "MALAWI" },
    { label: "Malaysia", value: "MALAYSIA" },
    { label: "Maldives", value: "MALDIVES" },
    { label: "Mali", value: "MALI" },
    { label: "Malta", value: "MALTA" },
    { label: "Marshall Islands", value: "MARSHALL_ISLANDS" },
    { label: "Mauritania", value: "MAURITANIA" },
    { label: "Mauritius", value: "MAURITIUS" },
    { label: "Mexico", value: "MEXICO" },
    { label: "Micronesia", value: "MICRONESIA" },
    { label: "Moldova", value: "MOLDOVA" },
    { label: "Monaco", value: "MONACO" },
    { label: "Mongolia", value: "MONGOLIA" },
    { label: "Montenegro", value: "MONTENEGRO" },
    { label: "Morocco", value: "MOROCCO" },
    { label: "Mozambique", value: "MOZAMBIQUE" },
    { label: "Myanmar", value: "MYANMAR" },

    { label: "Namibia", value: "NAMIBIA" },
    { label: "Nauru", value: "NAURU" },
    { label: "Nepal", value: "NEPAL" },
    { label: "Netherlands", value: "NETHERLANDS" },
    { label: "New Zealand", value: "NEW_ZEALAND" },
    { label: "Nicaragua", value: "NICARAGUA" },
    { label: "Niger", value: "NIGER" },
    { label: "Nigeria", value: "NIGERIA" },
    { label: "North Korea", value: "NORTH_KOREA" },
    { label: "North Macedonia", value: "NORTH_MACEDONIA" },
    { label: "Norway", value: "NORWAY" },

    { label: "Oman", value: "OMAN" },

    { label: "Pakistan", value: "PAKISTAN" },
    { label: "Palau", value: "PALAU" },
    { label: "Panama", value: "PANAMA" },
    { label: "Papua New Guinea", value: "PAPUA_NEW_GUINEA" },
    { label: "Paraguay", value: "PARAGUAY" },
    { label: "Peru", value: "PERU" },
    { label: "Philippines", value: "PHILIPPINES" },
    { label: "Poland", value: "POLAND" },
    { label: "Portugal", value: "PORTUGAL" },

    { label: "Qatar", value: "QATAR" },

    { label: "Romania", value: "ROMANIA" },
    { label: "Russia", value: "RUSSIA" },
    { label: "Rwanda", value: "RWANDA" },

    { label: "Saint Kitts and Nevis", value: "SAINT_KITTS_AND_NEVIS" },
    { label: "Saint Lucia", value: "SAINT_LUCIA" },
    {
        label: "Saint Vincent and the Grenadines",
        value: "SAINT_VINCENT_AND_THE_GRENADINES",
    },
    { label: "Samoa", value: "SAMOA" },
    { label: "San Marino", value: "SAN_MARINO" },
    { label: "Sao Tome and Principe", value: "SAO_TOME_AND_PRINCIPE" },
    { label: "Saudi Arabia", value: "SAUDI_ARABIA" },
    { label: "Senegal", value: "SENEGAL" },
    { label: "Serbia", value: "SERBIA" },
    { label: "Seychelles", value: "SEYCHELLES" },
    { label: "Sierra Leone", value: "SIERRA_LEONE" },
    { label: "Singapore", value: "SINGAPORE" },
    { label: "Slovakia", value: "SLOVAKIA" },
    { label: "Slovenia", value: "SLOVENIA" },
    { label: "Solomon Islands", value: "SOLOMON_ISLANDS" },
    { label: "Somalia", value: "SOMALIA" },
    { label: "South Africa", value: "SOUTH_AFRICA" },
    { label: "South Korea", value: "SOUTH_KOREA" },
    { label: "South Sudan", value: "SOUTH_SUDAN" },
    { label: "Spain", value: "SPAIN" },
    { label: "Sri Lanka", value: "SRI_LANKA" },
    { label: "Sudan", value: "SUDAN" },
    { label: "Suriname", value: "SURINAME" },
    { label: "Sweden", value: "SWEDEN" },
    { label: "Switzerland", value: "SWITZERLAND" },
    { label: "Syria", value: "SYRIA" },

    { label: "Taiwan", value: "TAIWAN" },
    { label: "Tajikistan", value: "TAJIKISTAN" },
    { label: "Tanzania", value: "TANZANIA" },
    { label: "Thailand", value: "THAILAND" },
    { label: "Timor-Leste", value: "TIMOR_LESTE" },
    { label: "Togo", value: "TOGO" },
    { label: "Tonga", value: "TONGA" },
    { label: "Trinidad and Tobago", value: "TRINIDAD_AND_TOBAGO" },
    { label: "Tunisia", value: "TUNISIA" },
    { label: "Turkey", value: "TURKEY" },
    { label: "Turkmenistan", value: "TURKMENISTAN" },
    { label: "Tuvalu", value: "TUVALU" },

    { label: "Uganda", value: "UGANDA" },
    { label: "Ukraine", value: "UKRAINE" },
    { label: "United Arab Emirates", value: "UNITED_ARAB_EMIRATES" },
    { label: "United Kingdom", value: "UNITED_KINGDOM" },
    { label: "United States", value: "UNITED_STATES" },
    { label: "Uruguay", value: "URUGUAY" },
    { label: "Uzbekistan", value: "UZBEKISTAN" },

    { label: "Vanuatu", value: "VANUATU" },
    { label: "Vatican City", value: "VATICAN_CITY" },
    { label: "Venezuela", value: "VENEZUELA" },
    { label: "Vietnam", value: "VIETNAM" },

    { label: "Yemen", value: "YEMEN" },

    { label: "Zambia", value: "ZAMBIA" },
    { label: "Zimbabwe", value: "ZIMBABWE" },
];


const UK_COUNTY_OPTIONS: Option[] = [
    // England
    { label: "Bedfordshire", value: "BEDFORDSHIRE" },
    { label: "Berkshire", value: "BERKSHIRE" },
    { label: "Bristol", value: "BRISTOL" },
    { label: "Buckinghamshire", value: "BUCKINGHAMSHIRE" },
    { label: "Cambridgeshire", value: "CAMBRIDGESHIRE" },
    { label: "Cheshire", value: "CHESHIRE" },
    { label: "Cornwall", value: "CORNWALL" },
    { label: "County Durham", value: "COUNTY_DURHAM" },
    { label: "Cumbria", value: "CUMBRIA" },
    { label: "Derbyshire", value: "DERBYSHIRE" },
    { label: "Devon", value: "DEVON" },
    { label: "Dorset", value: "DORSET" },
    { label: "East Riding of Yorkshire", value: "EAST_RIDING_OF_YORKSHIRE" },
    { label: "East Sussex", value: "EAST_SUSSEX" },
    { label: "Essex", value: "ESSEX" },
    { label: "Gloucestershire", value: "GLOUCESTERSHIRE" },
    { label: "Greater London", value: "GREATER_LONDON" },
    { label: "Greater Manchester", value: "GREATER_MANCHESTER" },
    { label: "Hampshire", value: "HAMPSHIRE" },
    { label: "Herefordshire", value: "HEREFORDSHIRE" },
    { label: "Hertfordshire", value: "HERTFORDSHIRE" },
    { label: "Isle of Wight", value: "ISLE_OF_WIGHT" },
    { label: "Kent", value: "KENT" },
    { label: "Lancashire", value: "LANCASHIRE" },
    { label: "Leicestershire", value: "LEICESTERSHIRE" },
    { label: "Lincolnshire", value: "LINCOLNSHIRE" },
    { label: "Merseyside", value: "MERSEYSIDE" },
    { label: "Norfolk", value: "NORFOLK" },
    { label: "North Yorkshire", value: "NORTH_YORKSHIRE" },
    { label: "Northamptonshire", value: "NORTHAMPTONSHIRE" },
    { label: "Northumberland", value: "NORTHUMBERLAND" },
    { label: "Nottinghamshire", value: "NOTTINGHAMSHIRE" },
    { label: "Oxfordshire", value: "OXFORDSHIRE" },
    { label: "Rutland", value: "RUTLAND" },
    { label: "Shropshire", value: "SHROPSHIRE" },
    { label: "Somerset", value: "SOMERSET" },
    { label: "South Yorkshire", value: "SOUTH_YORKSHIRE" },
    { label: "Staffordshire", value: "STAFFORDSHIRE" },
    { label: "Suffolk", value: "SUFFOLK" },
    { label: "Surrey", value: "SURREY" },
    { label: "Tyne and Wear", value: "TYNE_AND_WEAR" },
    { label: "Warwickshire", value: "WARWICKSHIRE" },
    { label: "West Midlands", value: "WEST_MIDLANDS" },
    { label: "West Sussex", value: "WEST_SUSSEX" },
    { label: "West Yorkshire", value: "WEST_YORKSHIRE" },
    { label: "Wiltshire", value: "WILTSHIRE" },
    { label: "Worcestershire", value: "WORCESTERSHIRE" },

    // Wales
    { label: "Anglesey", value: "ANGLESEY" },
    { label: "Blaenau Gwent", value: "BLAENAU_GWENT" },
    { label: "Bridgend", value: "BRIDGEND" },
    { label: "Caerphilly", value: "CAERPHILLY" },
    { label: "Cardiff", value: "CARDIFF" },
    { label: "Carmarthenshire", value: "CARMARTHENSHIRE" },
    { label: "Ceredigion", value: "CEREDIGION" },
    { label: "Conwy", value: "CONWY" },
    { label: "Denbighshire", value: "DENBIGHSHIRE" },
    { label: "Flintshire", value: "FLINTSHIRE" },
    { label: "Gwynedd", value: "GWYNEDD" },
    { label: "Merthyr Tydfil", value: "MERTHYR_TYDFIL" },
    { label: "Monmouthshire", value: "MONMOUTHSHIRE" },
    { label: "Neath Port Talbot", value: "NEATH_PORT_TALBOT" },
    { label: "Newport", value: "NEWPORT" },
    { label: "Pembrokeshire", value: "PEMBROKESHIRE" },
    { label: "Powys", value: "POWYS" },
    { label: "Rhondda Cynon Taf", value: "RHONDDA_CYNON_TAF" },
    { label: "Swansea", value: "SWANSEA" },
    { label: "Torfaen", value: "TORFAEN" },
    { label: "Vale of Glamorgan", value: "VALE_OF_GLAMORGAN" },
    { label: "Wrexham", value: "WREXHAM" },

    // Scotland
    { label: "Aberdeen City", value: "ABERDEEN_CITY" },
    { label: "Aberdeenshire", value: "ABERDEENSHIRE" },
    { label: "Angus", value: "ANGUS" },
    { label: "Argyll and Bute", value: "ARGYLL_AND_BUTE" },
    { label: "Clackmannanshire", value: "CLACKMANNANSHIRE" },
    { label: "Dumfries and Galloway", value: "DUMFRIES_AND_GALLOWAY" },
    { label: "Dundee City", value: "DUNDEE_CITY" },
    { label: "East Ayrshire", value: "EAST_AYRSHIRE" },
    { label: "East Dunbartonshire", value: "EAST_DUNBARTONSHIRE" },
    { label: "East Lothian", value: "EAST_LOTHIAN" },
    { label: "East Renfrewshire", value: "EAST_RENFREWSHIRE" },
    { label: "Edinburgh", value: "EDINBURGH" },
    { label: "Falkirk", value: "FALKIRK" },
    { label: "Fife", value: "FIFE" },
    { label: "Glasgow City", value: "GLASGOW_CITY" },
    { label: "Highland", value: "HIGHLAND" },
    { label: "Inverclyde", value: "INVERCLYDE" },
    { label: "Midlothian", value: "MIDLOTHIAN" },
    { label: "Moray", value: "MORAY" },
    { label: "North Ayrshire", value: "NORTH_AYRSHIRE" },
    { label: "North Lanarkshire", value: "NORTH_LANARKSHIRE" },
    { label: "Orkney Islands", value: "ORKNEY_ISLANDS" },
    { label: "Perth and Kinross", value: "PERTH_AND_KINROSS" },
    { label: "Renfrewshire", value: "RENFREWSHIRE" },
    { label: "Scottish Borders", value: "SCOTTISH_BORDERS" },
    { label: "Shetland Islands", value: "SHETLAND_ISLANDS" },
    { label: "South Ayrshire", value: "SOUTH_AYRSHIRE" },
    { label: "South Lanarkshire", value: "SOUTH_LANARKSHIRE" },
    { label: "Stirling", value: "STIRLING" },
    { label: "West Dunbartonshire", value: "WEST_DUNBARTONSHIRE" },
    { label: "West Lothian", value: "WEST_LOTHIAN" },
    { label: "Western Isles", value: "WESTERN_ISLES" },

    // Northern Ireland
    { label: "Antrim and Newtownabbey", value: "ANTRIM_AND_NEWTOWNABBEY" },
    { label: "Ards and North Down", value: "ARDS_AND_NORTH_DOWN" },
    { label: "Armagh City, Banbridge and Craigavon", value: "ARMAGH_CITY_BANBRIDGE_AND_CRAIGAVON" },
    { label: "Belfast", value: "BELFAST" },
    { label: "Causeway Coast and Glens", value: "CAUSEWAY_COAST_AND_GLENS" },
    { label: "Derry and Strabane", value: "DERRY_AND_STRABANE" },
    { label: "Fermanagh and Omagh", value: "FERMANAGH_AND_OMAGH" },
    { label: "Lisburn and Castlereagh", value: "LISBURN_AND_CASTLEREAGH" },
    { label: "Mid and East Antrim", value: "MID_AND_EAST_ANTRIM" },
    { label: "Mid Ulster", value: "MID_ULSTER" },
    { label: "Newry, Mourne and Down", value: "NEWRY_MOURNE_AND_DOWN" },
];



const UNIVERSITY_SUBJECT_OPTIONS: Option[] = [
    { label: "N/A", value: "NA" },
    { label: "Science (general)", value: "SCIENCE" },
    { label: "Engineering", value: "ENGINEERING" },
    { label: "Arts", value: "ARTS" },
    { label: "Medicine", value: "MEDICINE" },
    { label: "Business", value: "BUSINESS" },
    { label: "Law", value: "LAW" },
    { label: "Computer Science", value: "COMPUTER_SCIENCE" },
    { label: "Mathematics", value: "MATHEMATICS" },
    { label: "Physics", value: "PHYSICS" },
    { label: "Chemistry", value: "CHEMISTRY" },
    { label: "Biology", value: "BIOLOGY" },
    { label: "Economics", value: "ECONOMICS" },
    { label: "Psychology", value: "PSYCHOLOGY" },
    { label: "Philosophy", value: "PHILOSOPHY" },
    { label: "Sociology", value: "SOCIOLOGY" },
    { label: "Political Science", value: "POLITICAL_SCIENCE" },
    { label: "Literature", value: "LITERATURE" },
    { label: "History", value: "HISTORY" },
    { label: "Geography", value: "GEOGRAPHY" },
    { label: "Education", value: "EDUCATION" },
    { label: "Nursing", value: "NURSING" },
    {
        label: "Environmental Science",
        value: "ENVIRONMENTAL_SCIENCE",
    },
    { label: "Journalism", value: "JOURNALISM" },
    { label: "Fine Arts", value: "FINE_ARTS" },
    { label: "Music", value: "MUSIC" },
    { label: "Theater", value: "THEATER" },
    { label: "Anthropology", value: "ANTHROPOLOGY" },
    { label: "Linguistics", value: "LINGUISTICS" },
    { label: "Astronomy", value: "ASTRONOMY" },
    { label: "Agriculture", value: "AGRICULTURE" },
    { label: "Other", value: "OTHER" },
];

export default function UserCharacteristicsScreen() {
    const router = useRouter();
    const { user, setHasCharacteristics } = useAuth();

    // stepper
    const TOTAL_STEPS = 5;
    const [step, setStep] = useState(0); // 0..4
    const [animOpacity] = React.useState(new Animated.Value(1));
    const [animTranslate] = React.useState(new Animated.Value(0));

    // free-text location
    const [country, setCountry] = useState("");
    const [city, setCity] = useState("");

    // simple fields
    const [ageRange, setAgeRange] = useState<string | null>(null);
    const [gender, setGender] = useState<string | null>(null);
    const [genderSelfDescribe, setGenderSelfDescribe] = useState("");

    const [education, setEducation] = useState<string | null>(null);
    const [occupation, setOccupation] = useState<string | null>(null);
    const [newsFrequencyScore, setNewsFrequencyScore] = useState<number | null>(
        null
    );


    // enum fields (single-select)
    const [sexAtBirth, setSexAtBirth] = useState<string | null>(null);
    const [height, setHeight] = useState<string | null>(null);
    const [weightRange, setWeightRange] = useState<string | null>(null);
    const [incomeRange, setIncomeRange] = useState<string | null>(null);
    const [parent, setParent] = useState<string | null>(null);
    const [eyeColor, setEyeColor] = useState<string | null>(null);
    const [countryOfBirth, setCountryOfBirth] = useState<string | null>(null);
    const [ukCounty, setUkCounty] = useState<string | null>(null);
    const [universitySubject, setUniversitySubject] = useState<string | null>(
        null
    );

    // enum fields (multi-select)
    const [raceSelections, setRaceSelections] = useState<string[]>([]);

    const [submitting, setSubmitting] = useState(false);

    const toggleRace = (value: string) => {
        setRaceSelections((current) =>
            current.includes(value)
                ? current.filter((v) => v !== value)
                : [...current, value]
        );
    };

    // global required check for final submit
    const requiredOk =
        country.trim().length > 0 &&
        ageRange !== null &&
        gender !== null &&
        raceSelections.length > 0 &&
        sexAtBirth !== null &&
        height !== null &&
        weightRange !== null &&
        incomeRange !== null;

    const handleSubmit = async () => {
        if (!user) {
            Alert.alert("Sign up required", "Please create an account first.");
            router.replace("/auth/signup");
            return;
        }

        if (!requiredOk) {
            Alert.alert(
                "Missing information",
                "Please complete the required fields marked with *."
            );
            return;
        }

        setSubmitting(true);
        try {
            const payload = {
                userId: user.id,
                location: { country, city },
                ageRange,
                gender,
                genderSelfDescribe:
                    gender === "Prefer to self-describe" ? genderSelfDescribe : "",
                education,
                occupation,
                newsFrequency: newsFrequencyScore,

                race: raceSelections,
                sexAtBirth,
                height,
                weightRange,
                incomeRange,
                parent,
                eyeColor,
                countryOfBirth,
                ukCounty,
                universitySubject,
            };

            // TODO: send payload to backend
            // await api.saveUserCharacteristics(payload);

            setHasCharacteristics(true);
            router.replace("/home");
        } catch (err) {
            console.error(err);
            Alert.alert("Error", "Could not save your details. Please try again.");
        } finally {
            setSubmitting(false);
        }
    };
    const animateToStep = (nextStep: number) => {
        if (nextStep === step) return;

        const direction = nextStep > step ? 1 : -1;

        // animate current card out
        Animated.parallel([
            Animated.timing(animOpacity, {
                toValue: 0,
                duration: 150,
                useNativeDriver: true,
            }),
            Animated.timing(animTranslate, {
                toValue: -20 * direction,
                duration: 150,
                useNativeDriver: true,
            }),
        ]).start(() => {
            // update step AFTER animation
            setStep(nextStep);

            // reset animation position for next card
            animTranslate.setValue(20 * direction);
            animOpacity.setValue(0);

            // animate card into view
            Animated.parallel([
                Animated.timing(animOpacity, {
                    toValue: 1,
                    duration: 200,
                    useNativeDriver: true,
                }),
                Animated.timing(animTranslate, {
                    toValue: 0,
                    duration: 200,
                    useNativeDriver: true,
                }),
            ]).start();
        });
    };

    const goNext = () => {
        if (step < TOTAL_STEPS - 1) {
            animateToStep(step + 1);
        } else {
            handleSubmit();
        }
    };

    const goBack = () => {
        if (step > 0) {
            animateToStep(step - 1);
        }
    };


    const progress = ((step + 1) / TOTAL_STEPS) * 100;

    const primaryCtaLabel =
        step === TOTAL_STEPS - 1 ? (submitting ? "Saving…" : "Save & continue") : "Next";

    return (
        <LinearGradient
            colors={["#FFE4B8", "#F7F2FF"]} // warm yellow → soft lilac
            start={{ x: 0, y: 0 }}
            end={{ x: 1, y: 0 }}
            style={styles.screen}
        >
            <View style={styles.overlay}>
                {/* Header */}
                <View style={styles.header}>
                <Text style={styles.headerLogo}>YourSay</Text>
                <View style={styles.headerTextWrap}>
                    <Text style={styles.headerTitle}>Tell us about you</Text>
                    <Text style={styles.headerSubtitle}>
                        This helps us understand which kinds of people agree or disagree
                        with news stories. Your personal details are never shown publicly.
                    </Text>
                </View>

                {/* Progress bar */}
                <View style={styles.progressBarOuter}>
                    <View style={[styles.progressBarInner, { width: `${progress}%` }]} />
                </View>
                <Text style={styles.progressLabel}>
                    Step {step + 1} of {TOTAL_STEPS}
                </Text>
            </View>

            {/* Content */}
            <ScrollView
                style={styles.scroll}
                contentContainerStyle={styles.scrollContent}
                showsVerticalScrollIndicator={false}
            >
                <Animated.View
                    style={[
                        styles.stepContainer,
                        {
                            opacity: animOpacity,
                            transform: [{ translateX: animTranslate }],
                        },
                    ]}
                >
                {/* STEP 1 – Basics */}
                {step === 0 && (
                    <SectionCard title="Where do you live?">
                        <Label text="Country *" />
                        <TextInput
                            placeholder="Country you live in"
                            placeholderTextColor="#9CA3AF"
                            value={country}
                            onChangeText={setCountry}
                            style={styles.input}
                        />

                        <Label text="City / region (optional)" />
                        <TextInput
                            placeholder="City or region"
                            placeholderTextColor="#9CA3AF"
                            value={city}
                            onChangeText={setCity}
                            style={styles.input}
                        />
                    </SectionCard>
                )}

                {/* STEP 2 – You */}
                {step === 1 && (
                    <SectionCard title="Who you are">
                        <Label text="Age range *" />
                        <ChipRowSimple
                            options={AGE_RANGES}
                            selected={ageRange}
                            onSelect={setAgeRange}
                        />

                        <View style={styles.divider} />

                        <Label text="Gender *" />
                        <ChipRowSimple
                            options={GENDER_OPTIONS}
                            selected={gender}
                            onSelect={setGender}
                        />
                        {gender === "Prefer to self-describe" && (
                            <View style={{ marginTop: 8 }}>
                                <Label text="Describe your gender" />
                                <TextInput
                                    placeholder="Type here"
                                    placeholderTextColor="#9CA3AF"
                                    value={genderSelfDescribe}
                                    onChangeText={setGenderSelfDescribe}
                                    style={styles.input}
                                />
                            </View>
                        )}

                        <View style={styles.divider} />

                        <Label text="Sex assigned at birth *" />
                        <ChipRowOption
                            options={SEX_AT_BIRTH_OPTIONS}
                            selected={sexAtBirth}
                            onSelect={setSexAtBirth}
                        />
                    </SectionCard>
                )}

                {/* STEP 3 – Background */}
                {step === 2 && (
                    <SectionCard title="Background">
                        <Label text="Race / ethnicity *" />
                        <View style={styles.chipWrap}>
                            {RACE_OPTIONS.map((opt) => (
                                <SelectableChip
                                    key={opt.value}
                                    label={opt.label}
                                    selected={raceSelections.includes(opt.value)}
                                    onPress={() => toggleRace(opt.value)}
                                />
                            ))}
                        </View>
                        <Text style={styles.helperText}>
                            You can select more than one option.
                        </Text>

                        <View style={styles.divider} />

                        <Dropdown
                            label="Country of birth *"
                            placeholder="Select your country of birth"
                            options={COUNTRY_OF_BIRTH_OPTIONS}
                            selected={countryOfBirth}
                            onSelect={setCountryOfBirth}
                        />

                        <View style={styles.divider} />

                        <Dropdown
                            label="UK county (if applicable)"
                            placeholder="Select your county (if you live in the UK)"
                            options={UK_COUNTY_OPTIONS}
                            selected={ukCounty}
                            onSelect={setUkCounty}
                        />

                    </SectionCard>
                )}

                {/* STEP 4 – Body & finances */}
                {step === 3 && (
                    <SectionCard title="Body & finances">
                        <Label text="Height *" />
                        <ChipRowOption
                            options={HEIGHT_OPTIONS}
                            selected={height}
                            onSelect={setHeight}
                        />

                        <View style={styles.divider} />

                        <Label text="Weight range *" />
                        <ChipRowOption
                            options={WEIGHT_OPTIONS}
                            selected={weightRange}
                            onSelect={setWeightRange}
                        />

                        <View style={styles.divider} />

                        <Label text="Annual household income *" />
                        <ChipRowOption
                            options={INCOME_OPTIONS}
                            selected={incomeRange}
                            onSelect={setIncomeRange}
                        />
                    </SectionCard>
                )}

                {/* STEP 5 – Family, education & news */}
                {step === 4 && (
                    <>
                        <SectionCard title="Family & extras">
                            <Label text="Are you a parent?" />
                            <ChipRowOption
                                options={PARENT_OPTIONS}
                                selected={parent}
                                onSelect={setParent}
                            />

                            <View style={styles.divider} />

                            <Label text="Eye colour" />
                            <ChipRowOption
                                options={EYE_COLOR_OPTIONS}
                                selected={eyeColor}
                                onSelect={setEyeColor}
                            />

                            <View style={styles.divider} />

                            <Dropdown
                                label="University subject (if applicable)"
                                placeholder="Select your subject"
                                options={UNIVERSITY_SUBJECT_OPTIONS}
                                selected={universitySubject}
                                onSelect={setUniversitySubject}
                            />

                        </SectionCard>

                        <SectionCard title="Education & news habits">
                            <Label text="Highest level of education" />
                            <ChipRowSimple
                                options={EDUCATION_OPTIONS}
                                selected={education}
                                onSelect={setEducation}
                            />

                            <View style={styles.divider} />

                            <Label text="Occupation" />
                            <ChipRowSimple
                                options={OCCUPATION_OPTIONS}
                                selected={occupation}
                                onSelect={setOccupation}
                            />

                            <View style={styles.divider} />

                            <ScaleSelector
                                question="How often do you follow the news?"
                                subtitle="Be honest 😉"
                                value={newsFrequencyScore}
                                onChange={setNewsFrequencyScore}
                                leftLabel="Almost never"
                                rightLabel="All the time"
                            />

                        </SectionCard>
                    </>
                )}
                </Animated.View>
                <View style={{ height: 100 }} />
            </ScrollView>

            {/* Sticky bottom controls */}
            <View style={styles.footer}>
                <View style={styles.footerRow}>
                    <TouchableOpacity
                        onPress={goBack}
                        disabled={step === 0 || submitting}
                        style={[
                            styles.secondaryButton,
                            (step === 0 || submitting) && styles.secondaryButtonDisabled,
                        ]}
                    >
                        <Text style={styles.secondaryText}>Back</Text>
                    </TouchableOpacity>

                    <TouchableOpacity
                        onPress={goNext}
                        disabled={submitting}
                        style={[
                            styles.submitButton,
                            submitting && styles.submitButtonDisabled,
                        ]}
                    >
                        <Text style={styles.submitText}>{primaryCtaLabel}</Text>
                        {step === TOTAL_STEPS - 1 && (
                            <Text style={styles.submitSubtext}>You can always edit later</Text>
                        )}
                    </TouchableOpacity>
                </View>
            </View>
            </View>
        </LinearGradient>
    );
}

/* ---------- helper components ---------- */

function SectionCard({
                         title,
                         children,
                     }: {
    title: string;
    children: React.ReactNode;
}) {
    return (
        <View style={styles.sectionCard}>
            <View style={styles.sectionHeaderRow}>
                <Text style={styles.sectionTitle}>{title}</Text>
            </View>
            {children}
        </View>
    );
}

function Label({ text }: { text: string }) {
    return <Text style={styles.label}>{text}</Text>;
}

/** For simple string options (age, gender, etc.) */
function ChipRowSimple({
                           options,
                           selected,
                           onSelect,
                       }: {
    options: string[];
    selected: string | null;
    onSelect: (value: string) => void;
}) {
    return (
        <View style={styles.chipWrap}>
            {options.map((label) => (
                <SelectableChip
                    key={label}
                    label={label}
                    selected={selected === label}
                    onPress={() => onSelect(label)}
                />
            ))}
        </View>
    );
}

/** For enum-backed Option[] lists (value is enum constant) */
function ChipRowOption({
                           options,
                           selected,
                           onSelect,
                       }: {
    options: Option[];
    selected: string | null;
    onSelect: (value: string) => void;
}) {
    return (
        <View style={styles.chipWrap}>
            {options.map((opt) => (
                <SelectableChip
                    key={opt.value}
                    label={opt.label}
                    selected={selected === opt.value}
                    onPress={() => onSelect(opt.value)}
                />
            ))}
        </View>
    );
}
type DropdownProps = {
    label: string;
    placeholder?: string;
    options: Option[];
    selected: string | null;
    onSelect: (value: string) => void;
};

function Dropdown({
                      label,
                      placeholder = "Select an option",
                      options,
                      selected,
                      onSelect,
                  }: DropdownProps) {
    const [open, setOpen] = React.useState(false);

    const selectedLabel =
        selected && options.find((o) => o.value === selected)?.label;

    return (
        <View style={{ marginTop: 12 }}>
            <Label text={label} />
            <Pressable
                onPress={() => setOpen(true)}
                style={styles.dropdownTrigger}
            >
                <Text
                    style={[
                        styles.dropdownTriggerText,
                        !selectedLabel && { color: "#9CA3AF" },
                    ]}
                >
                    {selectedLabel || placeholder}
                </Text>
            </Pressable>

            <Modal
                visible={open}
                transparent
                animationType="fade"
                onRequestClose={() => setOpen(false)}
            >
                <Pressable
                    style={styles.modalBackdrop}
                    onPress={() => setOpen(false)}
                >
                    <View style={styles.modalSheet}>
                        <Text style={styles.modalTitle}>{label}</Text>
                        <ScrollView style={{ maxHeight: 320 }}>
                            {options.map((opt) => (
                                <Pressable
                                    key={opt.value}
                                    onPress={() => {
                                        onSelect(opt.value);
                                        setOpen(false);
                                    }}
                                    style={[
                                        styles.modalOption,
                                        selected === opt.value && styles.modalOptionSelected,
                                    ]}
                                >
                                    <Text
                                        style={[
                                            styles.modalOptionText,
                                            selected === opt.value && styles.modalOptionTextSelected,
                                        ]}
                                    >
                                        {opt.label}
                                    </Text>
                                </Pressable>
                            ))}
                        </ScrollView>
                    </View>
                </Pressable>
            </Modal>
        </View>
    );
}
type ScaleSelectorProps = {
    question: string;
    subtitle?: string;
    min?: number;
    max?: number;
    value: number | null;
    onChange: (value: number) => void;
    leftLabel?: string;
    rightLabel?: string;
};

function ScaleSelector({
                           question,
                           subtitle,
                           min = 0,
                           max = 10,
                           value,
                           onChange,
                           leftLabel,
                           rightLabel,
                       }: ScaleSelectorProps) {
    const numbers = [];
    for (let i = min; i <= max; i++) {
        numbers.push(i);
    }

    return (
        <View style={styles.scaleContainer}>
            <Text style={styles.scaleQuestion}>{question}</Text>
            {subtitle ? (
                <Text style={styles.scaleSubtitle}>{subtitle}</Text>
            ) : null}

            <View style={styles.scaleRow}>
                {numbers.map((n) => {
                    const selected = value === n;
                    return (
                        <TouchableOpacity
                            key={n}
                            onPress={() => onChange(n)}
                            style={[
                                styles.scaleBox,
                                selected && styles.scaleBoxSelected,
                            ]}
                        >
                            <Text
                                style={[
                                    styles.scaleBoxText,
                                    selected && styles.scaleBoxTextSelected,
                                ]}
                            >
                                {n}
                            </Text>
                        </TouchableOpacity>
                    );
                })}
            </View>

            <View style={styles.scaleLabelsRow}>
                <Text style={styles.scaleSideLabel}>
                    {leftLabel || ""}
                </Text>
                <Text style={styles.scaleSideLabel}>
                    {rightLabel || ""}
                </Text>
            </View>
        </View>
    );
}

/* ---------- styles ---------- */

const styles = StyleSheet.create({
    /* root containers */
    screen: {
        flex: 1, // gradient comes from LinearGradient wrapper
    },
    overlay: {
        flex: 1,
        paddingTop: 20,
    },

    /* header */
    header: {
        paddingHorizontal: 24,
        paddingBottom: 16,
    },
    stepContainer: {
        flex: 1,
    },

    headerLogo: {
        fontSize: 20,
        fontWeight: "700",
        color: "#5B21FF", // purple accent
        marginBottom: 8,
    },
    headerTextWrap: {
        marginBottom: 12,
    },
    headerTitle: {
        fontSize: 28,
        fontWeight: "700",
        color: "#1F2933",
        marginBottom: 4,
    },
    headerSubtitle: {
        fontSize: 14,
        color: "#4B5563",
    },
    progressBarOuter: {
        marginTop: 12,
        height: 6,
        borderRadius: 999,
        backgroundColor: "rgba(15, 23, 42, 0.1)",
        overflow: "hidden",
    },
    progressBarInner: {
        height: "100%",
        backgroundColor: "#5B21FF",
        borderRadius: 999,
    },
    progressLabel: {
        marginTop: 4,
        fontSize: 11,
        color: "#4B5563",
    },

    /* scroll + section cards */
    scroll: {
        flex: 1,
    },
    scrollContent: {
        paddingHorizontal: 24,
        paddingBottom: 24,
    },
    sectionCard: {
        marginTop: 18,
        padding: 18,
        borderRadius: 20,
        backgroundColor: "rgba(255, 255, 255, 0.82)",
        borderWidth: 1,
        borderColor: "rgba(148, 163, 184, 0.4)",
        shadowColor: "#000",
        shadowOpacity: 0.12,
        shadowRadius: 18,
        shadowOffset: { width: 0, height: 12 },
        elevation: 3,
    },
    sectionHeaderRow: {
        flexDirection: "row",
        alignItems: "center",
        marginBottom: 8,
    },
    sectionTitle: {
        fontSize: 18,
        fontWeight: "600",
        color: "#111827",
    },
    label: {
        fontSize: 14,
        fontWeight: "500",
        color: "#374151",
        marginBottom: 4,
        marginTop: 8,
    },
    helperText: {
        fontSize: 11,
        color: "#6B7280",
        marginTop: 4,
    },
    divider: {
        height: 1,
        backgroundColor: "rgba(148, 163, 184, 0.45)",
        marginVertical: 12,
    },

    input: {
        borderWidth: 1,
        borderColor: "rgba(148, 163, 184, 0.7)",
        borderRadius: 12,
        paddingHorizontal: 12,
        paddingVertical: 10,
        fontSize: 14,
        color: "#111827",
        backgroundColor: "rgba(255, 255, 255, 0.9)",
        marginBottom: 4,
    },

    chipWrap: {
        flexDirection: "row",
        flexWrap: "wrap",
        marginTop: 4,
    },

    /* footer buttons */
    footer: {
        position: "absolute",
        left: 0,
        right: 0,
        bottom: 0,
        paddingHorizontal: 24,
        paddingBottom: 24,
        paddingTop: 10,
        backgroundColor: "rgba(247, 247, 252, 0.96)",
        borderTopWidth: 1,
        borderTopColor: "rgba(148, 163, 184, 0.35)",
    },
    footerRow: {
        flexDirection: "row",
        alignItems: "center",
        gap: 10,
    },
    secondaryButton: {
        flex: 1,
        borderRadius: 999,
        paddingVertical: 10,
        alignItems: "center",
        borderWidth: 1,
        borderColor: "rgba(148, 163, 184, 0.9)",
        backgroundColor: "rgba(255, 255, 255, 0.7)",
    },
    secondaryButtonDisabled: {
        opacity: 0.4,
    },
    secondaryText: {
        color: "#374151",
        fontWeight: "500",
        fontSize: 14,
    },
    submitButton: {
        flex: 2,
        borderRadius: 999,
        paddingVertical: 12,
        paddingHorizontal: 18,
        backgroundColor: "#111827",
        alignItems: "center",
    },
    submitButtonDisabled: {
        backgroundColor: "#6B7280",
    },
    submitText: {
        color: "#F9FAFB",
        fontWeight: "700",
        fontSize: 16,
    },
    submitSubtext: {
        marginTop: 2,
        fontSize: 11,
        color: "#E5E7EB",
    },

    /* dropdown styles */
    dropdownTrigger: {
        borderRadius: 12,
        borderWidth: 1,
        borderColor: "rgba(148, 163, 184, 0.8)",
        paddingHorizontal: 12,
        paddingVertical: 12,
        backgroundColor: "rgba(255, 255, 255, 0.9)",
        marginTop: 4,
    },
    dropdownTriggerText: {
        fontSize: 14,
        color: "#111827",
    },
    modalBackdrop: {
        flex: 1,
        backgroundColor: "rgba(15, 23, 42, 0.35)",
        justifyContent: "center",
        paddingHorizontal: 24,
    },
    modalSheet: {
        borderRadius: 18,
        padding: 16,
        backgroundColor: "#F9FAFB",
        maxHeight: "80%",
    },
    modalTitle: {
        fontSize: 16,
        fontWeight: "600",
        color: "#111827",
        marginBottom: 10,
    },
    modalOption: {
        paddingVertical: 10,
        paddingHorizontal: 8,
        borderRadius: 10,
        marginBottom: 4,
    },
    modalOptionSelected: {
        backgroundColor: "rgba(79, 70, 229, 0.1)",
    },
    modalOptionText: {
        fontSize: 14,
        color: "#111827",
    },
    modalOptionTextSelected: {
        color: "#4F46E5",
        fontWeight: "600",
    },

    /* scale selector (0–10 Typeform-style) */
    scaleContainer: {
        marginTop: 8,
    },
    scaleQuestion: {
        fontSize: 20,
        fontWeight: "600",
        color: "#111827",
        marginBottom: 4,
    },
    scaleSubtitle: {
        fontSize: 14,
        color: "#6B7280",
        marginBottom: 16,
    },
    scaleRow: {
        flexDirection: "row",
        flexWrap: "wrap",
        gap: 8,
    },
    scaleBox: {
        width: 40,
        height: 48,
        borderRadius: 10,
        borderWidth: 1,
        borderColor: "rgba(148, 163, 184, 0.8)",
        alignItems: "center",
        justifyContent: "center",
        backgroundColor: "rgba(255, 255, 255, 0.8)",
    },
    scaleBoxSelected: {
        backgroundColor: "#111827",
        borderColor: "#111827",
    },
    scaleBoxText: {
        fontSize: 16,
        color: "#4B5563",
        fontWeight: "500",
    },
    scaleBoxTextSelected: {
        color: "#F9FAFB",
    },
    scaleLabelsRow: {
        flexDirection: "row",
        justifyContent: "space-between",
        marginTop: 8,
    },
    scaleSideLabel: {
        fontSize: 12,
        color: "#6B7280",
    },
});
