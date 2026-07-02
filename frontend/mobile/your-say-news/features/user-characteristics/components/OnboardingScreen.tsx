import React, { useMemo, useRef, useState } from "react";
import {
    View,
    Text,
    ScrollView,
    Pressable,
    StyleSheet,
    Alert,
    Animated,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useRouter } from "expo-router";

import {
    useTheme,
    getEditorial,
    EditorialFont,
    AnimationDuration,
} from "@/constants/theme";
import { useAuthStore } from "@/features/auth";

import { buildCharacteristicAnswers, isRequiredComplete, type OnboardingForm } from "../answers";
import {
    AGE_RANGES,
    GENDER_OPTIONS,
    SEX_AT_BIRTH_OPTIONS,
    RACE_OPTIONS,
    SEXUAL_ORIENTATION_OPTIONS,
    MARITAL_STATUS_OPTIONS,
    COUNTRY_OF_BIRTH_OPTIONS,
    URBAN_RURAL_OPTIONS,
    RELIGION_OPTIONS,
    RELIGIOSITY_OPTIONS,
    POLITICAL_PERSUASION_OPTIONS,
    EDUCATION_OPTIONS,
    OCCUPATION_OPTIONS,
    EMPLOYMENT_SECTOR_OPTIONS,
    UNIVERSITY_SUBJECT_OPTIONS,
    HEIGHT_OPTIONS,
    WEIGHT_OPTIONS,
    INCOME_OPTIONS,
    EYE_COLOR_OPTIONS,
    PARENT_OPTIONS,
    YES_NO_OPTIONS,
    PET_TYPE_OPTIONS,
    CHRONOTYPE_OPTIONS,
    OUTLOOK_OPTIONS,
    NEURODIVERGENCE_TYPE_OPTIONS,
    DISABILITY_TYPE_OPTIONS,
    HOUSING_STATUS_OPTIONS,
    PROPERTY_TYPE_OPTIONS,
    CURRENCY_OPTIONS,
    NATIONALITY_OPTIONS,
} from "../data/options";
import { submitCharacteristics } from "../services/CharacteristicService";
import { Eyebrow } from "@/components/ui";
import { PrivacyNote } from "./PrivacyNote";
import { WizardChipRow, WizardChipMultiRow } from "./WizardChipRow";
import { WizardInput } from "./WizardInput";
import { WizardScale } from "./WizardScale";
import { SearchableSelect } from "./SearchableSelect";
import { NewsSourceSlider } from "./NewsSourceSlider";

const STEP_META = [
    { title: "Where in the world?", subtitle: "Used to compare regions — never to locate you." },
    { title: "A little about you", subtitle: "Age and gender, as bands — never exact details." },
    { title: "How you identify", subtitle: "Pick all that apply. Only ever shown in aggregate." },
    { title: "Your background", subtitle: "Where you’re from and where you hold citizenship." },
    { title: "Beliefs & politics", subtitle: "Used only in anonymous aggregate breakdowns." },
    { title: "Education & work", subtitle: "What you studied and the work you do." },
    { title: "Body & finances", subtitle: "Pick your currency first." },
    { title: "Quirky questions", subtitle: "The fun stuff — only ever shown in aggregate." },
    { title: "News habits", subtitle: "How you consume news and your relationship with it." },
    { title: "Neurodiversity & disability", subtitle: "Only ever shown in anonymous aggregate." },
    { title: "Property", subtitle: "Your housing situation — only ever shown in aggregate." },
];
const TOTAL_STEPS = STEP_META.length;

/**
 * The Characteristics Wizard ("Set up your lens") — an eleven-step editorial onboarding that collects
 * the full characteristic set, including the Stage-1 coverage axes (politics, religion, urban/rural,
 * marital status, sexual orientation, citizenship, employment sector), a Quirky-questions step
 * (pets, chronotype, outlook), a neurodiversity & disability step and a closing property step.
 *
 * PII separation: only the characteristic answers are submitted — never identity, which travels in
 * the bearer token (see CharacteristicService). Every step carries the persistent privacy note.
 */
export function OnboardingScreen() {
    const router = useRouter();
    const { isDark } = useTheme();
    const e = getEditorial(isDark);
    const setHasOnboarded = useAuthStore((s) => s.setHasOnboarded);
    const setHasCharacteristics = useAuthStore((s) => s.setHasCharacteristics);

    const [step, setStep] = useState(0);
    const fade = useRef(new Animated.Value(1)).current;

    // Location
    const [country, setCountry] = useState(""); // human label, e.g. "United Kingdom"
    const [city, setCity] = useState("");
    const [region, setRegion] = useState("");
    const [urbanRural, setUrbanRural] = useState<string | null>(null);
    // Who you are
    const [ageRange, setAgeRange] = useState<string | null>(null);
    const [gender, setGender] = useState<string | null>(null);
    const [sexAtBirth, setSexAtBirth] = useState<string | null>(null);
    const [sexualOrientation, setSexualOrientation] = useState<string | null>(null);
    const [maritalStatus, setMaritalStatus] = useState<string | null>(null);
    const [raceSelections, setRaceSelections] = useState<string[]>([]);
    // Background
    const [countryOfBirth, setCountryOfBirth] = useState<string | null>(null);
    const [citizenship, setCitizenship] = useState<string | null>(null);
    const [religion, setReligion] = useState<string | null>(null);
    const [religiosity, setReligiosity] = useState<string | null>(null);
    const [politicalPersuasion, setPoliticalPersuasion] = useState<string | null>(null);
    // Education & work
    const [education, setEducation] = useState<string | null>(null);
    const [occupation, setOccupation] = useState<string | null>(null);
    const [employmentSector, setEmploymentSector] = useState<string | null>(null);
    const [universitySubject, setUniversitySubject] = useState<string | null>(null);
    // Finances & body
    const [personalIncomeRange, setPersonalIncomeRange] = useState<string | null>(null);
    const [householdIncomeRange, setHouseholdIncomeRange] = useState<string | null>(null);
    const [height, setHeight] = useState<string | null>(null);
    const [weightRange, setWeightRange] = useState<string | null>(null);
    const [eyeColor, setEyeColor] = useState<string | null>(null);
    const [parent, setParent] = useState<string | null>(null);
    const [hasPet, setHasPet] = useState<string | null>(null);
    const [petType, setPetType] = useState<string | null>(null);
    const [chronotype, setChronotype] = useState<string | null>(null);
    const [outlook, setOutlook] = useState<string | null>(null);
    const [neurodivergent, setNeurodivergent] = useState<string | null>(null);
    const [neurodivergenceType, setNeurodivergenceType] = useState<string | null>(null);
    const [hasDisability, setHasDisability] = useState<string | null>(null);
    const [disabilityType, setDisabilityType] = useState<string | null>(null);
    const [housingStatus, setHousingStatus] = useState<string | null>(null);
    const [propertyType, setPropertyType] = useState<string | null>(null);
    const [newsFrequencyScore, setNewsFrequencyScore] = useState<number | null>(null);
    const [balancedNewsViewpoint, setBalancedNewsViewpoint] = useState<string | null>(null);
    const [mainstreamNewsPercent, setMainstreamNewsPercent] = useState(50);
    const [betterWorldWithData, setBetterWorldWithData] = useState<string | null>(null);
    const [currency, setCurrency] = useState("USD");

    const [submitting, setSubmitting] = useState(false);

    const currencyCode = CURRENCY_OPTIONS.find((c) => c.value === currency)?.symbol ?? "USD";
    const incomeOptions = INCOME_OPTIONS.map((o) => ({
        ...o,
        label: o.label.replace(/(\d+(?:k|M)?)/g, `${currencyCode} $1`),
    }));

    const toggleRace = (value: string) =>
        setRaceSelections((cur) =>
            cur.includes(value) ? cur.filter((v) => v !== value) : [...cur, value]
        );

    const countryValue = useMemo(
        () => COUNTRY_OF_BIRTH_OPTIONS.find((o) => o.label === country)?.value ?? null,
        [country]
    );

    const form: OnboardingForm = {
        country,
        city,
        region,
        ukCounty: null,
        urbanRural,
        ageRange,
        gender,
        sexAtBirth,
        sexualOrientation,
        maritalStatus,
        raceSelections,
        countryOfBirth,
        citizenship,
        religion,
        religiosity,
        politicalPersuasion,
        education,
        occupation,
        employmentSector,
        universitySubject,
        personalIncomeRange,
        householdIncomeRange,
        height,
        weightRange,
        eyeColor,
        parent,
        hasPet,
        petType,
        chronotype,
        outlook,
        neurodivergent,
        neurodivergenceType,
        hasDisability,
        disabilityType,
        housingStatus,
        propertyType,
        newsFrequencyScore,
        balancedNewsViewpoint,
        mainstreamNewsPercent,
        betterWorldWithData,
    };

    const animateTo = (next: number) => {
        Animated.timing(fade, {
            toValue: 0,
            duration: AnimationDuration.fast,
            useNativeDriver: true,
        }).start(() => {
            setStep(next);
            Animated.timing(fade, {
                toValue: 1,
                duration: AnimationDuration.normal,
                useNativeDriver: true,
            }).start();
        });
    };

    const handleSubmit = async () => {
        if (!isRequiredComplete(form)) {
            Alert.alert(
                "A few required answers are missing",
                "Please complete every required characteristic. City, region and university subject can be skipped when they do not apply."
            );
            return;
        }
        setSubmitting(true);
        try {
            await submitCharacteristics(buildCharacteristicAnswers(form));
            setHasCharacteristics(true);
            setHasOnboarded(true);
            router.replace("/(protected)");
        } catch {
            Alert.alert("Couldn’t save", "Something went wrong saving your details. Please try again.");
        } finally {
            setSubmitting(false);
        }
    };

    const goNext = () => (step < TOTAL_STEPS - 1 ? animateTo(step + 1) : handleSubmit());
    const goBack = () => step > 0 && animateTo(step - 1);

    const isLast = step === TOTAL_STEPS - 1;
    const meta = STEP_META[step];

    return (
        <SafeAreaView style={[styles.screen, { backgroundColor: e.bg }]} edges={["top", "bottom"]}>
            {/* Header */}
            <View style={styles.header}>
                <View style={styles.headerTop}>
                    <Text style={[styles.lens, { color: e.ink }]}>Set up your lens</Text>
                    <Text style={[styles.stepLabel, { color: e.muted }]}>
                        STEP {step + 1} OF {TOTAL_STEPS}
                    </Text>
                </View>
                <View style={styles.segments}>
                    {Array.from({ length: TOTAL_STEPS }).map((_, i) => (
                        <View
                            key={i}
                            style={[
                                styles.segment,
                                { backgroundColor: i <= step ? (isDark ? e.lime : e.ink) : e.border },
                            ]}
                        />
                    ))}
                </View>
                <Text style={[styles.title, { color: e.ink }]}>{meta.title}</Text>
                <Text style={[styles.subtitle, { color: e.secondary }]}>{meta.subtitle}</Text>
            </View>

            {/* Body */}
            <Animated.View style={[styles.bodyWrap, { opacity: fade }]}>
                <ScrollView
                    contentContainerStyle={styles.bodyContent}
                    showsVerticalScrollIndicator={false}
                >
                    {step === 0 && (
                        <View style={styles.fields}>
                            <SearchableSelect
                                label="Country or region you live in *"
                                placeholder="Search 195 countries"
                                options={COUNTRY_OF_BIRTH_OPTIONS}
                                selected={countryValue}
                                onSelect={(v) =>
                                    setCountry(COUNTRY_OF_BIRTH_OPTIONS.find((o) => o.value === v)?.label ?? "")
                                }
                            />
                            <WizardInput
                                label="City / nearest city"
                                placeholder="Optional"
                                value={city}
                                onChangeText={setCity}
                            />
                            <WizardInput
                                label="Region / state / county"
                                placeholder="Optional"
                                value={region}
                                onChangeText={setRegion}
                            />
                            <Field label="Settlement type *">
                                <WizardChipRow options={URBAN_RURAL_OPTIONS} selected={urbanRural} onSelect={setUrbanRural} />
                            </Field>
                        </View>
                    )}

                    {step === 1 && (
                        <View style={styles.fields}>
                            <Field label="Age range *">
                                <WizardChipRow options={AGE_RANGES} selected={ageRange} onSelect={setAgeRange} />
                            </Field>
                            <Field label="Gender *">
                                <WizardChipRow options={GENDER_OPTIONS} selected={gender} onSelect={setGender} />
                            </Field>
                            <Field label="Sex assigned at birth *">
                                <WizardChipRow options={SEX_AT_BIRTH_OPTIONS} selected={sexAtBirth} onSelect={setSexAtBirth} />
                            </Field>
                        </View>
                    )}

                    {step === 2 && (
                        <View style={styles.fields}>
                            <Field
                                label="Race / ethnicity *"
                                trailing={
                                    raceSelections.length > 0
                                        ? `${raceSelections.length} SELECTED`
                                        : "SELECT ALL THAT APPLY"
                                }
                            >
                                <WizardChipMultiRow options={RACE_OPTIONS} selected={raceSelections} onToggle={toggleRace} />
                            </Field>
                            <Field label="Sexual orientation *">
                                <WizardChipRow
                                    options={SEXUAL_ORIENTATION_OPTIONS}
                                    selected={sexualOrientation}
                                    onSelect={setSexualOrientation}
                                />
                            </Field>
                            <Field label="Relationship status *">
                                <WizardChipRow options={MARITAL_STATUS_OPTIONS} selected={maritalStatus} onSelect={setMaritalStatus} />
                            </Field>
                        </View>
                    )}

                    {step === 3 && (
                        <View style={styles.fields}>
                            <SearchableSelect
                                label="Country of birth *"
                                placeholder="Search 195 countries"
                                options={COUNTRY_OF_BIRTH_OPTIONS}
                                selected={countryOfBirth}
                                onSelect={setCountryOfBirth}
                            />
                            <SearchableSelect
                                label="Citizenship / nationality *"
                                placeholder="Search nationalities"
                                options={NATIONALITY_OPTIONS}
                                selected={citizenship}
                                onSelect={setCitizenship}
                            />
                        </View>
                    )}

                    {step === 4 && (
                        <View style={styles.fields}>
                            <Field label="Political leaning *">
                                <WizardChipRow
                                    options={POLITICAL_PERSUASION_OPTIONS}
                                    selected={politicalPersuasion}
                                    onSelect={setPoliticalPersuasion}
                                />
                            </Field>
                            <Field label="Religion *">
                                <WizardChipRow options={RELIGION_OPTIONS} selected={religion} onSelect={setReligion} />
                            </Field>
                            <Field label="How important is religion to you? *">
                                <WizardChipRow options={RELIGIOSITY_OPTIONS} selected={religiosity} onSelect={setReligiosity} />
                            </Field>
                        </View>
                    )}

                    {step === 5 && (
                        <View style={styles.fields}>
                            <Field label="Highest education *">
                                <WizardChipRow options={EDUCATION_OPTIONS} selected={education} onSelect={setEducation} />
                            </Field>
                            <Field label="Employment status *">
                                <WizardChipRow options={OCCUPATION_OPTIONS} selected={occupation} onSelect={setOccupation} />
                            </Field>
                            <Field label="Industry / sector *">
                                <WizardChipRow options={EMPLOYMENT_SECTOR_OPTIONS} selected={employmentSector} onSelect={setEmploymentSector} />
                            </Field>
                            <SearchableSelect
                                label="University subject (if applicable)"
                                placeholder="Select your subject"
                                options={UNIVERSITY_SUBJECT_OPTIONS}
                                selected={universitySubject}
                                onSelect={setUniversitySubject}
                            />
                        </View>
                    )}

                    {step === 6 && (
                        <View style={styles.fields}>
                            <Field label="Height *">
                                <WizardChipRow options={HEIGHT_OPTIONS} selected={height} onSelect={setHeight} />
                            </Field>
                            <Field label="Weight *">
                                <WizardChipRow options={WEIGHT_OPTIONS} selected={weightRange} onSelect={setWeightRange} />
                            </Field>
                            <Field label="Currency">
                                <WizardChipRow options={CURRENCY_OPTIONS} selected={currency} onSelect={setCurrency} />
                            </Field>
                            <Field label="Annual personal income *">
                                <WizardChipRow
                                    options={incomeOptions}
                                    selected={personalIncomeRange}
                                    onSelect={setPersonalIncomeRange}
                                />
                            </Field>
                            <Field label="Annual household income *">
                                <WizardChipRow
                                    options={incomeOptions}
                                    selected={householdIncomeRange}
                                    onSelect={setHouseholdIncomeRange}
                                />
                            </Field>
                            <Field label="Eye colour *">
                                <WizardChipRow options={EYE_COLOR_OPTIONS} selected={eyeColor} onSelect={setEyeColor} />
                            </Field>
                            <Field label="Are you a parent? *">
                                <WizardChipRow options={PARENT_OPTIONS} selected={parent} onSelect={setParent} />
                            </Field>
                        </View>
                    )}

                    {step === 7 && (
                        <View style={styles.fields}>
                            <Field label="Do you have a pet? *">
                                <WizardChipRow
                                    options={YES_NO_OPTIONS}
                                    selected={hasPet}
                                    onSelect={(v) => {
                                        setHasPet(v);
                                        if (v !== "YES") setPetType(null);
                                    }}
                                />
                            </Field>
                            {hasPet === "YES" && (
                                <Field label="What kind of pet? *">
                                    <WizardChipRow options={PET_TYPE_OPTIONS} selected={petType} onSelect={setPetType} />
                                </Field>
                            )}
                            <Field label="Are you a morning person or a night owl? *">
                                <WizardChipRow options={CHRONOTYPE_OPTIONS} selected={chronotype} onSelect={setChronotype} />
                            </Field>
                            <Field label="Optimist or pessimist about the future? *">
                                <WizardChipRow options={OUTLOOK_OPTIONS} selected={outlook} onSelect={setOutlook} />
                            </Field>
                        </View>
                    )}

                    {step === 8 && (
                        <View style={styles.fields}>
                            <Field label="Do you feel you see a balanced viewpoint of news? *">
                                <WizardChipRow
                                    options={YES_NO_OPTIONS}
                                    selected={balancedNewsViewpoint}
                                    onSelect={setBalancedNewsViewpoint}
                                />
                            </Field>
                            <Field label="What percentage of the news you absorb comes from mainstream news vs social media? *">
                                <NewsSourceSlider
                                    value={mainstreamNewsPercent}
                                    onChange={setMainstreamNewsPercent}
                                />
                            </Field>
                            <Field label="How often do you follow the news? *">
                                <WizardScale value={newsFrequencyScore} onChange={setNewsFrequencyScore} />
                            </Field>
                            <Field label="Do you think the world will be a better place if we can see real representative data on how society feels about topics? *">
                                <WizardChipRow
                                    options={YES_NO_OPTIONS}
                                    selected={betterWorldWithData}
                                    onSelect={setBetterWorldWithData}
                                />
                            </Field>
                        </View>
                    )}

                    {step === 9 && (
                        <View style={styles.fields}>
                            <Field label="Are you neurodivergent? *">
                                <WizardChipRow
                                    options={YES_NO_OPTIONS}
                                    selected={neurodivergent}
                                    onSelect={(v) => {
                                        setNeurodivergent(v);
                                        if (v !== "YES") setNeurodivergenceType(null);
                                    }}
                                />
                            </Field>
                            {neurodivergent === "YES" && (
                                <Field label="Which best describes it? *">
                                    <WizardChipRow
                                        options={NEURODIVERGENCE_TYPE_OPTIONS}
                                        selected={neurodivergenceType}
                                        onSelect={setNeurodivergenceType}
                                    />
                                </Field>
                            )}
                            <Field label="Do you have a disability? *">
                                <WizardChipRow
                                    options={YES_NO_OPTIONS}
                                    selected={hasDisability}
                                    onSelect={(v) => {
                                        setHasDisability(v);
                                        if (v !== "YES") setDisabilityType(null);
                                    }}
                                />
                            </Field>
                            {hasDisability === "YES" && (
                                <Field label="Which best describes it? *">
                                    <WizardChipRow
                                        options={DISABILITY_TYPE_OPTIONS}
                                        selected={disabilityType}
                                        onSelect={setDisabilityType}
                                    />
                                </Field>
                            )}
                        </View>
                    )}

                    {step === 10 && (
                        <View style={styles.fields}>
                            <Field label="Do you… *">
                                <WizardChipRow
                                    options={HOUSING_STATUS_OPTIONS}
                                    selected={housingStatus}
                                    onSelect={(v) => {
                                        setHousingStatus(v);
                                        if (v !== "OWN") setPropertyType(null);
                                    }}
                                />
                            </Field>
                            {housingStatus === "OWN" && (
                                <Field label="Do you own a house or a flat? *">
                                    <WizardChipRow
                                        options={PROPERTY_TYPE_OPTIONS}
                                        selected={propertyType}
                                        onSelect={setPropertyType}
                                    />
                                </Field>
                            )}
                        </View>
                    )}
                </ScrollView>
            </Animated.View>

            {/* Privacy note + footer */}
            <View style={styles.footerArea}>
                <PrivacyNote />
                <View style={styles.footerRow}>
                    <Pressable
                        onPress={goBack}
                        disabled={step === 0 || submitting}
                        style={[
                            styles.backBtn,
                            { borderColor: e.border, opacity: step === 0 ? 0.4 : 1 },
                        ]}
                    >
                        <Text style={[styles.backArrow, { color: e.ink }]}>‹</Text>
                    </Pressable>
                    <Pressable
                        onPress={goNext}
                        disabled={submitting}
                        style={[styles.nextBtn, { backgroundColor: e.ink, opacity: submitting ? 0.7 : 1 }]}
                    >
                        <Text style={[styles.nextLabel, { color: e.bg }]}>
                            {isLast ? "Finish setup" : "Continue"}
                        </Text>
                        <Text style={[styles.nextArrow, { color: e.bg }]}>→</Text>
                    </Pressable>
                </View>
            </View>
        </SafeAreaView>
    );
}

/** A labelled field block with a mono eyebrow and optional trailing counter. */
function Field({
    label,
    trailing,
    children,
}: {
    label: string;
    trailing?: string;
    children: React.ReactNode;
}) {
    const { isDark } = useTheme();
    const e = getEditorial(isDark);
    return (
        <View style={styles.field}>
            <View style={styles.fieldHead}>
                <Eyebrow text={label} />
                {trailing && (
                    <Text style={[styles.trailing, { color: e.teal }]}>{trailing}</Text>
                )}
            </View>
            {children}
        </View>
    );
}

const styles = StyleSheet.create({
    screen: { flex: 1 },
    header: { paddingHorizontal: 26, paddingTop: 6 },
    headerTop: {
        flexDirection: "row",
        alignItems: "center",
        justifyContent: "space-between",
        marginBottom: 14,
    },
    lens: {
        fontFamily: EditorialFont.sansBold,
        fontWeight: "700",
        fontSize: 13,
        letterSpacing: 0.4,
    },
    stepLabel: { fontFamily: EditorialFont.mono, fontSize: 11 },
    segments: { flexDirection: "row", gap: 5, marginBottom: 20 },
    segment: { flex: 1, height: 4, borderRadius: 2 },
    title: {
        fontFamily: EditorialFont.serif,
        fontSize: 27,
        lineHeight: 30,
        letterSpacing: -0.3,
    },
    subtitle: {
        fontFamily: EditorialFont.sans,
        fontSize: 13.5,
        lineHeight: 20,
        marginTop: 8,
    },
    bodyWrap: { flex: 1 },
    bodyContent: { paddingHorizontal: 26, paddingTop: 22, paddingBottom: 24 },
    fields: { gap: 22 },
    field: { gap: 12 },
    fieldHead: {
        flexDirection: "row",
        alignItems: "center",
        justifyContent: "space-between",
    },
    trailing: { fontFamily: EditorialFont.monoSemiBold, fontSize: 10, fontWeight: "600" },
    footerArea: { paddingHorizontal: 26, paddingTop: 4, gap: 16 },
    footerRow: { flexDirection: "row", gap: 11, paddingBottom: 6 },
    backBtn: {
        width: 52,
        height: 52,
        borderRadius: 14,
        borderWidth: 1.5,
        alignItems: "center",
        justifyContent: "center",
    },
    backArrow: { fontSize: 24, lineHeight: 26, fontWeight: "700" },
    nextBtn: {
        flex: 1,
        height: 52,
        borderRadius: 14,
        flexDirection: "row",
        alignItems: "center",
        justifyContent: "center",
        gap: 9,
    },
    nextLabel: { fontFamily: EditorialFont.sansBold, fontWeight: "700", fontSize: 15 },
    nextArrow: { fontSize: 17, fontWeight: "700" },
});
