import React, { useState } from "react";
import { View, TextInput, StyleSheet } from "react-native";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";
import { Eyebrow } from "@/components/ui";

/** A labelled editorial free-text field. */
export function WizardInput({
    label,
    placeholder,
    value,
    onChangeText,
}: {
    label: string;
    placeholder?: string;
    value: string;
    onChangeText: (text: string) => void;
}) {
    const { isDark } = useTheme();
    const e = getEditorial(isDark);
    const [focused, setFocused] = useState(false);

    return (
        <View>
            <Eyebrow text={label} style={{ marginBottom: 8 }} />
            <TextInput
                value={value}
                onChangeText={onChangeText}
                placeholder={placeholder}
                placeholderTextColor={e.muted}
                onFocus={() => setFocused(true)}
                onBlur={() => setFocused(false)}
                style={[
                    styles.input,
                    {
                        backgroundColor: e.surface,
                        borderColor: focused ? e.focus : e.border,
                        color: e.ink,
                    },
                ]}
            />
        </View>
    );
}

const styles = StyleSheet.create({
    input: {
        height: 52,
        borderWidth: 1.5,
        borderRadius: 13,
        paddingHorizontal: 16,
        fontFamily: EditorialFont.sansSemiBold,
        fontWeight: "600",
        fontSize: 15,
    },
});
