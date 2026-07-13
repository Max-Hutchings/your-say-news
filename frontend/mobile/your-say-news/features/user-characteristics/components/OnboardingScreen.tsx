import React, { useCallback, useEffect, useState } from "react";
import {
    View,
    Text,
    ScrollView,
    Pressable,
    StyleSheet,
    Alert,
    Animated,
    ActivityIndicator,
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

import {
    buildCharacteristicAnswers,
    createEmptyOnboardingForm,
    findFirstIncompleteStep,
    isHigherEducation,
    type OnboardingForm,
} from "../answers";
import { CURRENCY_OPTIONS, YES_NO_OPTIONS } from "../data/options";
import type { CharacteristicOptions } from "../types";
import { submitCharacteristics } from "../services/CharacteristicService";
import { fetchCharacteristicOptions } from "../services/CharacteristicOptionsService";
import {
    clearOnboardingDraft,
    loadOnboardingDraft,
    saveOnboardingDraft,
} from "../services/OnboardingDraftService";
import { Eyebrow } from "@/components/ui";
import { PrivacyNote } from "./PrivacyNote";
import { WizardChipRow, WizardChipMultiRow } from "./WizardChipRow";
import { WizardInput } from "./WizardInput";
import { WizardScale } from "./WizardScale";
import { SearchableSelect } from "./SearchableSelect";
import { SearchableMultiSelect } from "./SearchableMultiSelect";
import { NewsSourceSlider } from "./NewsSourceSlider";

const STEP_META = [
    { title: "Where in the world?", subtitle: "Used to compare regions — never to locate you." },
    { title: "A little about you", subtitle: "Your age and gender. Age stays a band in reporting." },
    { title: "How you identify", subtitle: "Pick all that apply. Only ever shown in aggregate." },
    { title: "Your background", subtitle: "Where you’re from and the nationalities you hold." },
    { title: "Beliefs & politics", subtitle: "Used only in anonymous aggregate breakdowns." },
    { title: "Education & work", subtitle: "What you studied and the work you do." },
    { title: "Body basics", subtitle: "Broad bands only — never exact measurements." },
    { title: "Family & home life", subtitle: "Only ever shown in anonymous aggregate." },
    { title: "Quirky questions", subtitle: "The fun stuff — only ever shown in aggregate." },
    { title: "Finances", subtitle: "Pick your currency first." },
    { title: "News habits", subtitle: "How you consume news and your relationship with it." },
    { title: "Neurodiversity & disability", subtitle: "Only ever shown in anonymous aggregate." },
    { title: "Housing", subtitle: "Your housing situation — only ever shown in aggregate." },
];
const TOTAL_STEPS = STEP_META.length;

/**
 * The Characteristics Wizard ("Set up your lens") — a thirteen-step editorial onboarding that collects
 * the full reformed characteristic set: age as a number, self-describe gender, multi-select ethnicity
 * and nationality, expanded education/work/tenure options, a family & home-life step, a quirky step,
 * finances, news habits, a neurodiversity & disability step and a closing housing step.
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
    const userId = useAuthStore((s) => s.id);

    const [step, setStep] = useState(0);
    const [fade] = useState(() => new Animated.Value(1));
    const [form, setForm] = useState<OnboardingForm>(createEmptyOnboardingForm);
    const [draftLoaded, setDraftLoaded] = useState(userId == null);
    const [characteristicOptions, setCharacteristicOptions] = useState<CharacteristicOptions | null>(null);
    const [loadingOptions, setLoadingOptions] = useState(true);
    const [optionsError, setOptionsError] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [persisting, setPersisting] = useState(false);

    const setField = <K extends keyof OnboardingForm>(field: K, value: OnboardingForm[K]) => {
        setForm((current) => ({ ...current, [field]: value }));
    };

    useEffect(() => {
        if (userId == null) return;
        let active = true;
        loadOnboardingDraft(userId)
            .then((draft) => {
                if (!active || !draft) return;
                setForm({ ...createEmptyOnboardingForm(), ...draft.form });
                setStep(Math.max(0, Math.min(draft.nextStep, TOTAL_STEPS - 1)));
            })
            .finally(() => {
                if (active) setDraftLoaded(true);
            });
        return () => {
            active = false;
        };
    }, [userId]);

    const retryOptions = useCallback(async () => {
        setLoadingOptions(true);
        setOptionsError(false);
        try {
            setCharacteristicOptions(await fetchCharacteristicOptions());
        } catch {
            setCharacteristicOptions(null);
            setOptionsError(true);
        } finally {
            setLoadingOptions(false);
        }
    }, []);

    useEffect(() => {
        let active = true;
        fetchCharacteristicOptions()
            .then((options) => {
                if (active) setCharacteristicOptions(options);
            })
            .catch(() => {
                if (active) setOptionsError(true);
            })
            .finally(() => {
                if (active) setLoadingOptions(false);
            });
        return () => {
            active = false;
        };
    }, []);

    const fields = characteristicOptions?.fields;
    const COUNTRY_OF_BIRTH_OPTIONS = fields?.countryOfBirth ?? [];
    const URBAN_RURAL_OPTIONS = fields?.urbanRural ?? [];
    const GENDER_OPTIONS = fields?.gender ?? [];
    const SEX_AT_BIRTH_OPTIONS = fields?.sexAtBirth ?? [];
    const RACE_OPTIONS = fields?.race ?? [];
    const SEXUAL_ORIENTATION_OPTIONS = fields?.sexualOrientation ?? [];
    const MARITAL_STATUS_OPTIONS = fields?.maritalStatus ?? [];
    const NATIONALITY_OPTIONS = fields?.citizenship ?? [];
    const RELIGION_OPTIONS = fields?.religion ?? [];
    const RELIGIOSITY_OPTIONS = fields?.religiosity ?? [];
    const POLITICAL_PERSUASION_OPTIONS = fields?.politicalPersuasion ?? [];
    const EDUCATION_OPTIONS = fields?.education ?? [];
    const OCCUPATION_OPTIONS = fields?.occupation ?? [];
    const EMPLOYMENT_SECTOR_OPTIONS = fields?.employmentSector ?? [];
    const UNIVERSITY_SUBJECT_OPTIONS = fields?.universitySubject ?? [];
    const HEIGHT_OPTIONS = fields?.height ?? [];
    const WEIGHT_OPTIONS = fields?.weightRange ?? [];
    const INCOME_OPTIONS = fields?.incomeRange ?? [];
    const EYE_COLOR_OPTIONS = fields?.eyeColor ?? [];
    const PARENT_OPTIONS = fields?.parent ?? [];
    const PET_TYPE_OPTIONS = fields?.petType ?? [];
    const CHRONOTYPE_OPTIONS = fields?.chronotype ?? [];
    const OUTLOOK_OPTIONS = fields?.outlook ?? [];
    const NEURODIVERGENCE_TYPE_OPTIONS = fields?.neurodivergenceType ?? [];
    const DISABILITY_TYPE_OPTIONS = fields?.disabilityType ?? [];
    const HOUSING_STATUS_OPTIONS = fields?.housingStatus ?? [];
    const PROPERTY_TYPE_OPTIONS = fields?.propertyType ?? [];

    const currencyCode = CURRENCY_OPTIONS.find((c) => c.value === form.currency)?.symbol ?? "USD";
    const incomeOptions = INCOME_OPTIONS.map((o) => ({
        ...o,
        label: o.label.replace(/(\d+(?:k|M)?)/g, `${currencyCode} $1`),
    }));

    // Toggle a value in one of the multi-select array fields (ethnicity, nationality, pet, neuro, disability).
    const toggleIn = (field: "raceSelections" | "citizenship" | "petType" | "neurodivergenceType" | "disabilityType") =>
        (value: string) =>
            setForm((current) => {
                const list = current[field];
                return {
                    ...current,
                    [field]: list.includes(value)
                        ? list.filter((v) => v !== value)
                        : [...list, value],
                };
            });

    const toggleRace = toggleIn("raceSelections");
    const toggleCitizenship = toggleIn("citizenship");
    const togglePetType = toggleIn("petType");
    const toggleNeurodivergenceType = toggleIn("neurodivergenceType");
    const toggleDisabilityType = toggleIn("disabilityType");

    const countryValue = COUNTRY_OF_BIRTH_OPTIONS.find((o) => o.label === form.country)?.value ?? null;

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

    const showIncompleteAnswer = (incompleteStep: { step: number; fieldLabel: string }) => {
        Alert.alert("Answer required", `Please answer “${incompleteStep.fieldLabel}” before continuing.`);
        if (incompleteStep.step !== step) {
            animateTo(incompleteStep.step);
        }
    };

    const handleSubmit = async () => {
        if (!characteristicOptions) return;
        const incompleteStep = findFirstIncompleteStep(form, characteristicOptions.minimumAge);
        if (incompleteStep) {
            showIncompleteAnswer(incompleteStep);
            return;
        }
        setSubmitting(true);
        try {
            if (userId != null) {
                await saveOnboardingDraft(userId, form, step);
            }
            await submitCharacteristics(buildCharacteristicAnswers(form));
            if (userId != null) {
                await clearOnboardingDraft(userId);
            }
            setHasCharacteristics(true);
            setHasOnboarded(true);
            router.replace("/(protected)");
        } catch {
            Alert.alert("Couldn’t save", "Something went wrong saving your details. Please try again.");
        } finally {
            setSubmitting(false);
        }
    };

    const goNext = async () => {
        if (step === TOTAL_STEPS - 1) {
            await handleSubmit();
            return;
        }
        setPersisting(true);
        try {
            if (userId != null) {
                await saveOnboardingDraft(userId, form, step + 1);
            }
            animateTo(step + 1);
        } catch {
            Alert.alert("Couldn’t save your progress", "Please try again before continuing.");
        } finally {
            setPersisting(false);
        }
    };
    const goBack = () => step > 0 && animateTo(step - 1);

    const isLast = step === TOTAL_STEPS - 1;
    const meta = STEP_META[step];

    if (optionsError && !loadingOptions) {
        return (
            <SafeAreaView style={[styles.loadingScreen, { backgroundColor: e.bg }]}>
                <Text style={[styles.loadTitle, { color: e.ink }]}>We couldn’t load the questions</Text>
                <Text style={[styles.loadMessage, { color: e.secondary }]}>Check your connection, then try again.</Text>
                <Pressable
                    accessibilityRole="button"
                    onPress={() => void retryOptions()}
                    style={[styles.retryButton, { backgroundColor: e.ink }]}
                >
                    <Text style={[styles.retryLabel, { color: e.bg }]}>Try again</Text>
                </Pressable>
            </SafeAreaView>
        );
    }

    if (!draftLoaded || loadingOptions || !characteristicOptions) {
        return (
            <SafeAreaView style={[styles.loadingScreen, { backgroundColor: e.bg }]}>
                <ActivityIndicator color={e.lime} />
            </SafeAreaView>
        );
    }

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
                                label="Country of residence *"
                                placeholder="Search 195 countries"
                                options={COUNTRY_OF_BIRTH_OPTIONS}
                                selected={countryValue}
                                onSelect={(v) =>
                                    setField("country", COUNTRY_OF_BIRTH_OPTIONS.find((o) => o.value === v)?.label ?? "")
                                }
                            />
                            <WizardInput
                                label="City / nearest city"
                                placeholder="Optional"
                                value={form.city}
                                onChangeText={(value) => setField("city", value)}
                            />
                            <WizardInput
                                label="Region / state / county"
                                placeholder="Optional"
                                value={form.region}
                                onChangeText={(value) => setField("region", value)}
                            />
                            <Field label="Settlement type *">
                                <WizardChipRow options={URBAN_RURAL_OPTIONS} selected={form.urbanRural} onSelect={(value) => setField("urbanRural", value)} />
                            </Field>
                        </View>
                    )}

                    {step === 1 && (
                        <View style={styles.fields}>
                            <WizardInput
                                label="Your age *"
                                placeholder={`${characteristicOptions.minimumAge} or older`}
                                value={form.age?.toString() ?? ""}
                                onChangeText={(t) => {
                                    const digits = t.replace(/[^0-9]/g, "");
                                    setField("age", digits === "" ? null : Number(digits));
                                }}
                                keyboardType="number-pad"
                                maxLength={3}
                            />
                            <Field label="Gender *">
                                <WizardChipRow
                                    options={GENDER_OPTIONS}
                                    selected={form.gender}
                                    onSelect={(v) =>
                                        setForm((current) => ({
                                            ...current,
                                            gender: v,
                                            genderSelfDescribe: v === "SELF_DESCRIBE" ? current.genderSelfDescribe : "",
                                        }))
                                    }
                                />
                            </Field>
                            {form.gender === "SELF_DESCRIBE" && (
                                <WizardInput
                                    label="Describe your gender *"
                                    placeholder="How you identify"
                                    value={form.genderSelfDescribe}
                                    onChangeText={(value) => setField("genderSelfDescribe", value)}
                                    maxLength={60}
                                />
                            )}
                            <Field label="Sex registered at birth *">
                                <WizardChipRow options={SEX_AT_BIRTH_OPTIONS} selected={form.sexAtBirth} onSelect={(value) => setField("sexAtBirth", value)} />
                            </Field>
                        </View>
                    )}

                    {step === 2 && (
                        <View style={styles.fields}>
                            <Field
                                label="Ethnic background *"
                                trailing={
                                    form.raceSelections.length > 0
                                        ? `${form.raceSelections.length} SELECTED`
                                        : "SELECT ALL THAT APPLY"
                                }
                            >
                                <WizardChipMultiRow options={RACE_OPTIONS} selected={form.raceSelections} onToggle={toggleRace} />
                            </Field>
                            <Field label="Sexual orientation *">
                                <WizardChipRow
                                    options={SEXUAL_ORIENTATION_OPTIONS}
                                    selected={form.sexualOrientation}
                                    onSelect={(value) => setField("sexualOrientation", value)}
                                />
                            </Field>
                            <Field label="Relationship status *">
                                <WizardChipRow options={MARITAL_STATUS_OPTIONS} selected={form.maritalStatus} onSelect={(value) => setField("maritalStatus", value)} />
                            </Field>
                        </View>
                    )}

                    {step === 3 && (
                        <View style={styles.fields}>
                            <SearchableSelect
                                label="Country of birth *"
                                placeholder="Search 195 countries"
                                options={COUNTRY_OF_BIRTH_OPTIONS}
                                selected={form.countryOfBirth}
                                onSelect={(value) => setField("countryOfBirth", value)}
                            />
                            <SearchableMultiSelect
                                label="Nationality *"
                                placeholder="Search nationalities — pick all you hold"
                                options={NATIONALITY_OPTIONS}
                                selected={form.citizenship}
                                onToggle={toggleCitizenship}
                            />
                        </View>
                    )}

                    {step === 4 && (
                        <View style={styles.fields}>
                            <Field label="Political leaning *">
                                <WizardChipRow
                                    options={POLITICAL_PERSUASION_OPTIONS}
                                    selected={form.politicalPersuasion}
                                    onSelect={(value) => setField("politicalPersuasion", value)}
                                />
                            </Field>
                            <Field label="Religion *">
                                <WizardChipRow options={RELIGION_OPTIONS} selected={form.religion} onSelect={(value) => setField("religion", value)} />
                            </Field>
                            <Field label="How important is religion to you? *">
                                <WizardChipRow options={RELIGIOSITY_OPTIONS} selected={form.religiosity} onSelect={(value) => setField("religiosity", value)} />
                            </Field>
                        </View>
                    )}

                    {step === 5 && (
                        <View style={styles.fields}>
                            <Field label="Highest education *">
                                <WizardChipRow options={EDUCATION_OPTIONS} selected={form.education} onSelect={(value) => setField("education", value)} />
                            </Field>
                            <Field label="Current work or study status *">
                                <WizardChipRow options={OCCUPATION_OPTIONS} selected={form.occupation} onSelect={(value) => setField("occupation", value)} />
                            </Field>
                            <Field label="Industry / sector *">
                                <WizardChipRow options={EMPLOYMENT_SECTOR_OPTIONS} selected={form.employmentSector} onSelect={(value) => setField("employmentSector", value)} />
                            </Field>
                            {isHigherEducation(form.education) && (
                                <SearchableSelect
                                    label="University subject"
                                    placeholder="Select your subject"
                                    options={UNIVERSITY_SUBJECT_OPTIONS}
                                    selected={form.universitySubject}
                                    onSelect={(value) => setField("universitySubject", value)}
                                />
                            )}
                        </View>
                    )}

                    {step === 6 && (
                        <View style={styles.fields}>
                            <Field label="Height *">
                                <WizardChipRow options={HEIGHT_OPTIONS} selected={form.height} onSelect={(value) => setField("height", value)} />
                            </Field>
                            <Field label="Weight *">
                                <WizardChipRow options={WEIGHT_OPTIONS} selected={form.weightRange} onSelect={(value) => setField("weightRange", value)} />
                            </Field>
                            <Field label="Eye colour *">
                                <WizardChipRow options={EYE_COLOR_OPTIONS} selected={form.eyeColor} onSelect={(value) => setField("eyeColor", value)} />
                            </Field>
                        </View>
                    )}

                    {step === 7 && (
                        <View style={styles.fields}>
                            <Field label="Are you a parent or caregiver? *">
                                <WizardChipRow options={PARENT_OPTIONS} selected={form.parent} onSelect={(value) => setField("parent", value)} />
                            </Field>
                            <Field label="Do you have a pet? *">
                                <WizardChipRow
                                    options={YES_NO_OPTIONS}
                                    selected={form.hasPet}
                                    onSelect={(v) => {
                                        setForm((current) => ({ ...current, hasPet: v, petType: v === "YES" ? current.petType : [] }));
                                    }}
                                />
                            </Field>
                            {form.hasPet === "YES" && (
                                <Field
                                    label="What kinds of pet? *"
                                    trailing={
                                        form.petType.length > 0
                                            ? `${form.petType.length} SELECTED`
                                            : "SELECT ALL THAT APPLY"
                                    }
                                >
                                    <WizardChipMultiRow options={PET_TYPE_OPTIONS} selected={form.petType} onToggle={togglePetType} />
                                </Field>
                            )}
                        </View>
                    )}

                    {step === 8 && (
                        <View style={styles.fields}>
                            <Field label="Are you more of a morning or an evening person? *">
                                <WizardChipRow options={CHRONOTYPE_OPTIONS} selected={form.chronotype} onSelect={(value) => setField("chronotype", value)} />
                            </Field>
                            <Field label="How do you feel about the future? *">
                                <WizardChipRow options={OUTLOOK_OPTIONS} selected={form.outlook} onSelect={(value) => setField("outlook", value)} />
                            </Field>
                        </View>
                    )}

                    {step === 9 && (
                        <View style={styles.fields}>
                            <Field label="Currency">
                                <WizardChipRow options={CURRENCY_OPTIONS} selected={form.currency} onSelect={(value) => setField("currency", value)} />
                            </Field>
                            <Field label="Your annual personal income before tax *">
                                <WizardChipRow
                                    options={incomeOptions}
                                    selected={form.personalIncomeRange}
                                    onSelect={(value) => setField("personalIncomeRange", value)}
                                />
                            </Field>
                            <Field label="Total annual household income before tax *">
                                <WizardChipRow
                                    options={incomeOptions}
                                    selected={form.householdIncomeRange}
                                    onSelect={(value) => setField("householdIncomeRange", value)}
                                />
                            </Field>
                        </View>
                    )}

                    {step === 10 && (
                        <View style={styles.fields}>
                            <Field label="Do you regularly see more than one viewpoint on the news stories you follow? *">
                                <WizardChipRow
                                    options={YES_NO_OPTIONS}
                                    selected={form.balancedNewsViewpoint}
                                    onSelect={(value) => setField("balancedNewsViewpoint", value)}
                                />
                            </Field>
                            <Field label="Of the news you absorb, how much comes from mainstream news vs social media? *">
                                <NewsSourceSlider
                                    value={form.mainstreamNewsPercent}
                                    onChange={(value) => setField("mainstreamNewsPercent", value)}
                                />
                            </Field>
                            <Field label="How often do you follow the news? *">
                                <WizardScale value={form.newsFrequencyScore} onChange={(value) => setField("newsFrequencyScore", value)} />
                            </Field>
                            <Field label="Representative public-opinion data can help people understand society better. *">
                                <WizardChipRow
                                    options={YES_NO_OPTIONS}
                                    selected={form.betterWorldWithData}
                                    onSelect={(value) => setField("betterWorldWithData", value)}
                                />
                            </Field>
                        </View>
                    )}

                    {step === 11 && (
                        <View style={styles.fields}>
                            <Field label="Do you identify as neurodivergent, neurodiverse, or having a learning difference? *">
                                <WizardChipRow
                                    options={YES_NO_OPTIONS}
                                    selected={form.neurodivergent}
                                    onSelect={(v) => {
                                        setForm((current) => ({ ...current, neurodivergent: v, neurodivergenceType: v === "YES" ? current.neurodivergenceType : [] }));
                                    }}
                                />
                            </Field>
                            {form.neurodivergent === "YES" && (
                                <Field
                                    label="Which apply? *"
                                    trailing={
                                        form.neurodivergenceType.length > 0
                                            ? `${form.neurodivergenceType.length} SELECTED`
                                            : "SELECT ALL THAT APPLY"
                                    }
                                >
                                    <WizardChipMultiRow
                                        options={NEURODIVERGENCE_TYPE_OPTIONS}
                                        selected={form.neurodivergenceType}
                                        onToggle={toggleNeurodivergenceType}
                                    />
                                </Field>
                            )}
                            <Field label="Do you have a long-term condition, illness, impairment or day-to-day limitation? *">
                                <WizardChipRow
                                    options={YES_NO_OPTIONS}
                                    selected={form.hasDisability}
                                    onSelect={(v) => {
                                        setForm((current) => ({ ...current, hasDisability: v, disabilityType: v === "YES" ? current.disabilityType : [] }));
                                    }}
                                />
                            </Field>
                            {form.hasDisability === "YES" && (
                                <Field
                                    label="Which apply? *"
                                    trailing={
                                        form.disabilityType.length > 0
                                            ? `${form.disabilityType.length} SELECTED`
                                            : "SELECT ALL THAT APPLY"
                                    }
                                >
                                    <WizardChipMultiRow
                                        options={DISABILITY_TYPE_OPTIONS}
                                        selected={form.disabilityType}
                                        onToggle={toggleDisabilityType}
                                    />
                                </Field>
                            )}
                        </View>
                    )}

                    {step === 12 && (
                        <View style={styles.fields}>
                            <Field label="What is your housing situation? *">
                                <WizardChipRow
                                    options={HOUSING_STATUS_OPTIONS}
                                    selected={form.housingStatus}
                                    onSelect={(v) => {
                                        setForm((current) => ({ ...current, housingStatus: v, propertyType: v === "TEMPORARY_NO_FIXED" ? null : current.propertyType }));
                                    }}
                                />
                            </Field>
                            {form.housingStatus !== null && form.housingStatus !== "TEMPORARY_NO_FIXED" && (
                                <Field label="What type of home do you live in? *">
                                    <WizardChipRow
                                        options={PROPERTY_TYPE_OPTIONS}
                                        selected={form.propertyType}
                                        onSelect={(value) => setField("propertyType", value)}
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
                        disabled={step === 0 || submitting || persisting}
                        style={[
                            styles.backBtn,
                            { borderColor: e.border, opacity: step === 0 ? 0.4 : 1 },
                        ]}
                    >
                        <Text style={[styles.backArrow, { color: e.ink }]}>‹</Text>
                    </Pressable>
                    <Pressable
                        onPress={goNext}
                        disabled={submitting || persisting}
                        style={[styles.nextBtn, { backgroundColor: e.ink, opacity: submitting || persisting ? 0.7 : 1 }]}
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
    loadingScreen: { flex: 1, alignItems: "center", justifyContent: "center" },
    loadTitle: {
        fontFamily: EditorialFont.serif,
        fontSize: 26,
        textAlign: "center",
        marginBottom: 8,
    },
    loadMessage: {
        fontFamily: EditorialFont.sans,
        fontSize: 14,
        textAlign: "center",
        marginBottom: 22,
    },
    retryButton: { paddingHorizontal: 24, paddingVertical: 13, borderRadius: 4 },
    retryLabel: { fontFamily: EditorialFont.sansBold, fontSize: 14 },
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
