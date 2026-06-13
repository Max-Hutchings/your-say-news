import React, { useRef, useState } from "react";
import { View, ScrollView, StyleSheet, Alert, Animated } from "react-native";
import { useRouter } from "expo-router";
import { LinearGradient } from "expo-linear-gradient";

import { ThemedText } from "@/components/themed-text";
import { Button, Input } from "@/components/ui";
import {
    useTheme,
    Spacing,
    BorderRadius,
    BorderWidth,
    BrandColors,
    NeutralColors,
    AnimationDuration,
} from "@/constants/theme";
import { useAuthStore } from "@/features/auth";

import { buildCharacteristicAnswers, isRequiredComplete, type OnboardingForm } from "../answers";
import {
    AGE_RANGES,
    GENDER_OPTIONS,
    EDUCATION_OPTIONS,
    OCCUPATION_OPTIONS,
    RACE_OPTIONS,
    SEX_AT_BIRTH_OPTIONS,
    HEIGHT_OPTIONS,
    WEIGHT_OPTIONS,
    INCOME_OPTIONS,
    PARENT_OPTIONS,
    EYE_COLOR_OPTIONS,
    COUNTRY_OF_BIRTH_OPTIONS,
    UK_COUNTY_OPTIONS,
    UNIVERSITY_SUBJECT_OPTIONS,
} from "../data/options";
import { submitCharacteristics } from "../services/CharacteristicService";
import { SectionCard } from "./SectionCard";
import { FieldLabel } from "./FieldLabel";
import { ChipRowSimple } from "./ChipRowSimple";
import { ChipRowOption } from "./ChipRowOption";
import { SelectableChip } from "./SelectableChip";
import { Dropdown } from "./Dropdown";
import { ScaleSelector } from "./ScaleSelector";

const TOTAL_STEPS = 5;

/**
 * Multi-step onboarding that collects a user's characteristics.
 *
 * Personal identity is never bundled into the submission — only the
 * characteristic answers are sent (see CharacteristicService).
 */
export function OnboardingScreen() {
    const router = useRouter();
    const { isDark, colors } = useTheme();
    const setHasOnboarded = useAuthStore((s) => s.setHasOnboarded);

    // stepper
    const [step, setStep] = useState(0); // 0..4
    const animOpacity = useRef(new Animated.Value(1)).current;
    const animTranslate = useRef(new Animated.Value(0)).current;

    // free-text location
    const [country, setCountry] = useState("");
    const [city, setCity] = useState("");

    // simple fields
    const [ageRange, setAgeRange] = useState<string | null>(null);
    const [gender, setGender] = useState<string | null>(null);
    const [genderSelfDescribe, setGenderSelfDescribe] = useState("");
    const [education, setEducation] = useState<string | null>(null);
    const [occupation, setOccupation] = useState<string | null>(null);
    const [newsFrequencyScore, setNewsFrequencyScore] = useState<number | null>(null);

    // enum fields (single-select)
    const [sexAtBirth, setSexAtBirth] = useState<string | null>(null);
    const [height, setHeight] = useState<string | null>(null);
    const [weightRange, setWeightRange] = useState<string | null>(null);
    const [incomeRange, setIncomeRange] = useState<string | null>(null);
    const [parent, setParent] = useState<string | null>(null);
    const [eyeColor, setEyeColor] = useState<string | null>(null);
    const [countryOfBirth, setCountryOfBirth] = useState<string | null>(null);
    const [ukCounty, setUkCounty] = useState<string | null>(null);
    const [universitySubject, setUniversitySubject] = useState<string | null>(null);

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

    const form: OnboardingForm = {
        country,
        city,
        ageRange,
        gender,
        genderSelfDescribe,
        education,
        occupation,
        newsFrequencyScore,
        sexAtBirth,
        height,
        weightRange,
        incomeRange,
        parent,
        eyeColor,
        countryOfBirth,
        ukCounty,
        universitySubject,
        raceSelections,
    };

    const handleSubmit = async () => {
        if (!isRequiredComplete(form)) {
            Alert.alert(
                "Missing information",
                "Please complete the required fields marked with *."
            );
            return;
        }

        setSubmitting(true);
        try {
            await submitCharacteristics(buildCharacteristicAnswers(form));

            setHasOnboarded(true);
            router.replace("/(protected)");
        } catch {
            Alert.alert("Error", "Could not save your details. Please try again.");
        } finally {
            setSubmitting(false);
        }
    };

    const animateToStep = (nextStep: number) => {
        if (nextStep === step) return;

        const direction = nextStep > step ? 1 : -1;

        Animated.parallel([
            Animated.timing(animOpacity, {
                toValue: 0,
                duration: AnimationDuration.fast,
                useNativeDriver: true,
            }),
            Animated.timing(animTranslate, {
                toValue: -20 * direction,
                duration: AnimationDuration.fast,
                useNativeDriver: true,
            }),
        ]).start(() => {
            setStep(nextStep);
            animTranslate.setValue(20 * direction);
            animOpacity.setValue(0);

            Animated.parallel([
                Animated.timing(animOpacity, {
                    toValue: 1,
                    duration: AnimationDuration.normal,
                    useNativeDriver: true,
                }),
                Animated.timing(animTranslate, {
                    toValue: 0,
                    duration: AnimationDuration.normal,
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
    const isLastStep = step === TOTAL_STEPS - 1;
    const primaryCtaLabel = isLastStep ? "Save & continue" : "Next";

    const gradientColors = isDark
        ? [NeutralColors.slate[950], NeutralColors.slate[900], BrandColors.primary[950]]
        : [BrandColors.primary[50], NeutralColors.white, BrandColors.primary[100]];

    return (
        <LinearGradient colors={gradientColors} style={styles.screen}>
            <View style={styles.overlay}>
                {/* Header */}
                <View style={styles.header}>
                    <ThemedText variant="labelLarge" style={{ color: colors.brand.primary }}>
                        YourSay
                    </ThemedText>
                    <ThemedText variant="h2" style={styles.headerTitle}>
                        Tell us about you
                    </ThemedText>
                    <ThemedText variant="bodySmall" color="secondary">
                        This helps us understand which kinds of people agree or disagree with
                        news stories. Your personal details are never shown publicly.
                    </ThemedText>

                    {/* Progress bar */}
                    <View
                        style={[
                            styles.progressBarOuter,
                            { backgroundColor: colors.surface.tertiary },
                        ]}
                    >
                        <View
                            style={[
                                styles.progressBarInner,
                                { width: `${progress}%`, backgroundColor: colors.brand.primary },
                            ]}
                        />
                    </View>
                    <ThemedText variant="caption" color="tertiary" style={styles.progressLabel}>
                        Step {step + 1} of {TOTAL_STEPS}
                    </ThemedText>
                </View>

                {/* Content */}
                <ScrollView
                    style={styles.scroll}
                    contentContainerStyle={styles.scrollContent}
                    showsVerticalScrollIndicator={false}
                >
                    <Animated.View
                        style={{
                            opacity: animOpacity,
                            transform: [{ translateX: animTranslate }],
                        }}
                    >
                        {step === 0 && (
                            <SectionCard title="Where do you live?">
                                <Input
                                    label="Country *"
                                    placeholder="Country you live in"
                                    value={country}
                                    onChangeText={setCountry}
                                    containerStyle={styles.input}
                                />
                                <Input
                                    label="City / region (optional)"
                                    placeholder="City or region"
                                    value={city}
                                    onChangeText={setCity}
                                    containerStyle={styles.input}
                                />
                            </SectionCard>
                        )}

                        {step === 1 && (
                            <SectionCard title="Who you are">
                                <FieldLabel text="Age range *" />
                                <ChipRowSimple
                                    options={AGE_RANGES}
                                    selected={ageRange}
                                    onSelect={setAgeRange}
                                />

                                <Divider />

                                <FieldLabel text="Gender *" />
                                <ChipRowSimple
                                    options={GENDER_OPTIONS}
                                    selected={gender}
                                    onSelect={setGender}
                                />
                                {gender === "Prefer to self-describe" && (
                                    <Input
                                        label="Describe your gender"
                                        placeholder="Type here"
                                        value={genderSelfDescribe}
                                        onChangeText={setGenderSelfDescribe}
                                        containerStyle={styles.input}
                                    />
                                )}

                                <Divider />

                                <FieldLabel text="Sex assigned at birth *" />
                                <ChipRowOption
                                    options={SEX_AT_BIRTH_OPTIONS}
                                    selected={sexAtBirth}
                                    onSelect={setSexAtBirth}
                                />
                            </SectionCard>
                        )}

                        {step === 2 && (
                            <SectionCard title="Background">
                                <FieldLabel text="Race / ethnicity *" />
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
                                <ThemedText variant="caption" color="tertiary">
                                    You can select more than one option.
                                </ThemedText>

                                <Divider />

                                <Dropdown
                                    label="Country of birth *"
                                    placeholder="Select your country of birth"
                                    options={COUNTRY_OF_BIRTH_OPTIONS}
                                    selected={countryOfBirth}
                                    onSelect={setCountryOfBirth}
                                />

                                <Divider />

                                <Dropdown
                                    label="UK county (if applicable)"
                                    placeholder="Select your county (if you live in the UK)"
                                    options={UK_COUNTY_OPTIONS}
                                    selected={ukCounty}
                                    onSelect={setUkCounty}
                                />
                            </SectionCard>
                        )}

                        {step === 3 && (
                            <SectionCard title="Body & finances">
                                <FieldLabel text="Height *" />
                                <ChipRowOption
                                    options={HEIGHT_OPTIONS}
                                    selected={height}
                                    onSelect={setHeight}
                                />

                                <Divider />

                                <FieldLabel text="Weight range *" />
                                <ChipRowOption
                                    options={WEIGHT_OPTIONS}
                                    selected={weightRange}
                                    onSelect={setWeightRange}
                                />

                                <Divider />

                                <FieldLabel text="Annual household income *" />
                                <ChipRowOption
                                    options={INCOME_OPTIONS}
                                    selected={incomeRange}
                                    onSelect={setIncomeRange}
                                />
                            </SectionCard>
                        )}

                        {step === 4 && (
                            <>
                                <SectionCard title="Family & extras">
                                    <FieldLabel text="Are you a parent?" />
                                    <ChipRowOption
                                        options={PARENT_OPTIONS}
                                        selected={parent}
                                        onSelect={setParent}
                                    />

                                    <Divider />

                                    <FieldLabel text="Eye colour" />
                                    <ChipRowOption
                                        options={EYE_COLOR_OPTIONS}
                                        selected={eyeColor}
                                        onSelect={setEyeColor}
                                    />

                                    <Divider />

                                    <Dropdown
                                        label="University subject (if applicable)"
                                        placeholder="Select your subject"
                                        options={UNIVERSITY_SUBJECT_OPTIONS}
                                        selected={universitySubject}
                                        onSelect={setUniversitySubject}
                                    />
                                </SectionCard>

                                <SectionCard title="Education & news habits">
                                    <FieldLabel text="Highest level of education" />
                                    <ChipRowSimple
                                        options={EDUCATION_OPTIONS}
                                        selected={education}
                                        onSelect={setEducation}
                                    />

                                    <Divider />

                                    <FieldLabel text="Occupation" />
                                    <ChipRowSimple
                                        options={OCCUPATION_OPTIONS}
                                        selected={occupation}
                                        onSelect={setOccupation}
                                    />

                                    <Divider />

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
                    <View style={styles.scrollSpacer} />
                </ScrollView>

                {/* Sticky bottom controls */}
                <View
                    style={[
                        styles.footer,
                        {
                            backgroundColor: colors.background.primary,
                            borderTopColor: colors.border.primary,
                        },
                    ]}
                >
                    <View style={styles.footerRow}>
                        <Button
                            variant="outline"
                            onPress={goBack}
                            disabled={step === 0 || submitting}
                            style={styles.backButton}
                        >
                            Back
                        </Button>
                        <Button
                            onPress={goNext}
                            loading={submitting}
                            disabled={submitting}
                            style={styles.nextButton}
                        >
                            {primaryCtaLabel}
                        </Button>
                    </View>
                    {isLastStep && (
                        <ThemedText
                            variant="caption"
                            color="tertiary"
                            style={styles.footerHint}
                        >
                            You can always edit later
                        </ThemedText>
                    )}
                </View>
            </View>
        </LinearGradient>
    );
}

function Divider() {
    const { colors } = useTheme();
    return <View style={[styles.divider, { backgroundColor: colors.border.primary }]} />;
}

const styles = StyleSheet.create({
    screen: {
        flex: 1,
    },
    overlay: {
        flex: 1,
        paddingTop: Spacing.lg,
    },
    header: {
        paddingHorizontal: Spacing.xl,
        paddingBottom: Spacing.base,
        gap: Spacing.xs,
    },
    headerTitle: {
        marginTop: Spacing.xs,
    },
    progressBarOuter: {
        marginTop: Spacing.md,
        height: 6,
        borderRadius: BorderRadius.full,
        overflow: "hidden",
    },
    progressBarInner: {
        height: "100%",
        borderRadius: BorderRadius.full,
    },
    progressLabel: {
        marginTop: Spacing.xs,
    },
    scroll: {
        flex: 1,
    },
    scrollContent: {
        paddingHorizontal: Spacing.xl,
        paddingBottom: Spacing.xl,
    },
    scrollSpacer: {
        height: 100,
    },
    input: {
        marginTop: Spacing.sm,
    },
    chipWrap: {
        flexDirection: "row",
        flexWrap: "wrap",
        marginTop: Spacing.xs,
    },
    divider: {
        height: BorderWidth.thin,
        marginVertical: Spacing.md,
    },
    footer: {
        position: "absolute",
        left: 0,
        right: 0,
        bottom: 0,
        paddingHorizontal: Spacing.xl,
        paddingBottom: Spacing.xl,
        paddingTop: Spacing.sm,
        borderTopWidth: BorderWidth.thin,
    },
    footerRow: {
        flexDirection: "row",
        alignItems: "center",
        gap: Spacing.md,
    },
    backButton: {
        flex: 1,
    },
    nextButton: {
        flex: 2,
    },
    footerHint: {
        textAlign: "center",
        marginTop: Spacing.sm,
    },
});
