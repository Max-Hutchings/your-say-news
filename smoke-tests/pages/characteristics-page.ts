import { expect, type Page } from "@playwright/test";

export class CharacteristicsPage {
  constructor(private readonly page: Page) {}

  async acceptPrivacyPromise(): Promise<void> {
    await expect(
      this.page.getByText(/Anonymous by design/i)
    ).toBeVisible();
    await this.page
      .getByRole("button", { name: "I agree — continue" })
      .click();
    await expect(this.page.getByText("Set up your lens")).toBeVisible();
  }

  async completeRepresentativeProfile(): Promise<void> {
    await this.expectStep(1, "Where in the world?");
    await this.selectSearchOption("Search 195 countries", "United Kingdom");
    await this.selectChip("Settlement type *", "Urban");
    await this.continueTo(2);

    await this.page.getByPlaceholder(/or older/).fill("34");
    await this.selectChip("Gender *", "Non-binary");
    await this.selectChip("Sex registered at birth *", "Female");
    await this.continueTo(3);

    await this.selectChip(
      "Ethnic background *",
      "Mixed or multiple backgrounds"
    );
    await this.selectChip("Sexual orientation *", "Pansexual");
    await this.selectChip("Relationship status *", "Single");
    await this.continueTo(4);

    await this.selectSearchOption("Search 195 countries", "United Kingdom");
    await this.selectSearchOption(
      "Search nationalities — pick all you hold",
      "British",
      true
    );
    await this.continueTo(5);

    await this.selectChip("Political leaning *", "Centre-left");
    await this.selectChip("Religion *", "No religion");
    await this.selectChip(
      "How important is religion to you? *",
      "Not at all important"
    );
    await this.continueTo(6);

    await this.selectChip("Highest education *", "Secondary school");
    await this.selectChip(
      "Current work or study status *",
      "Employed full-time"
    );
    await this.selectChip("Industry / sector *", "IT & technology");
    await this.continueTo(7);

    await this.selectChip("Height *", "5'7\" – 5'9\" (170–175 cm)");
    await this.selectChip("Weight *", "70–79 kg");
    await this.selectChip("Eye colour *", "Hazel");
    await this.continueTo(8);

    await this.selectChip(
      "Are you a parent or caregiver? *",
      "Not a parent or caregiver"
    );
    await this.selectChip("Do you have a pet? *", "No");
    await this.continueTo(9);

    await this.selectChip(
      "Are you more of a morning or an evening person? *",
      "Mixed / depends"
    );
    await this.selectChip(
      "How do you feel about the future? *",
      "Mostly optimistic"
    );
    await this.continueTo(10);

    await this.selectFinanceAnswers();
    await this.continueTo(11);

    await this.selectChip(
      "Do you regularly see more than one viewpoint on the news stories you follow? *",
      "Yes"
    );
    await this.page
      .getByRole("slider", { name: "News following 7 of 10" })
      .click();
    await this.selectChip(
      "Representative public-opinion data can help people understand society better. *",
      "Yes",
      1
    );
    await this.continueTo(12);

    await this.selectChip(
      "Do you identify as neurodivergent, neurodiverse, or having a learning difference? *",
      "No"
    );
    await this.selectChip(
      "Do you have a long-term condition, illness, impairment or day-to-day limitation? *",
      "No",
      1
    );
    await this.continueTo(13);

    await this.selectChip(
      "What is your housing situation? *",
      "Private rent"
    );
    await this.selectChip(
      "What type of home do you live in? *",
      "Flat / apartment"
    );
  }

  async finishAndExpectFeed(): Promise<void> {
    const saved = this.page.waitForResponse(
      (response) =>
        response.request().method() === "POST" &&
        response.url().endsWith("/user-characteristics")
    );

    await this.page.getByText("Finish setup", { exact: true }).click();

    const response = await saved;
    expect(response.status()).toBe(201);
    await expect(response.json()).resolves.toMatchObject({
      country: "United Kingdom",
      urbanRural: "URBAN",
      age: 34,
      gender: "NON_BINARY",
      sexAtBirth: "FEMALE",
      sexualOrientation: "PANSEXUAL",
      maritalStatus: "SINGLE",
      race: ["MIXED_MULTIPLE"],
      citizenship: ["BRITISH"],
      politicalPersuasion: "CENTRE_LEFT",
      religion: "NO_RELIGION",
      education: "SECONDARY_SCHOOL",
      occupation: "EMPLOYED_FULL_TIME",
      employmentSector: "IT_TECHNOLOGY",
      hasPet: false,
      newsFrequency: 7,
      balancedNewsViewpoint: true,
      betterWorldWithData: true,
      housingStatus: "PRIVATE_RENT",
      propertyType: "FLAT_APARTMENT",
    });
    await expect(
      this.page.getByRole("button", { name: "Video posts" })
    ).toBeVisible();

    const onboardingStatus = this.page.waitForResponse(
      (candidate) =>
        candidate.request().method() === "GET" &&
        new URL(candidate.url()).pathname ===
          "/your-say-user/onboarding"
    );
    await this.page.reload();
    const persisted = await onboardingStatus;
    expect(persisted.status()).toBe(200);
    await expect(persisted.json()).resolves.toEqual({
      consented: true,
      hasCharacteristics: true,
      onboarded: true,
    });
    await expect(
      this.page.getByRole("button", { name: "Video posts" })
    ).toBeVisible();
  }

  private async selectFinanceAnswers(): Promise<void> {
    await this.selectChip("Currency", "GBP");

    const personalCurrent = this.page.getByRole("button", {
      name: "GBP 30k–GBP 40k",
    });
    if (await personalCurrent.count()) {
      await personalCurrent.first().click();
      await this.page
        .getByRole("button", { name: "GBP 50k–GBP 75k" })
        .last()
        .click();
      return;
    }

    await this.page
      .getByRole("button", { name: /GBP 25k to GBP 40k/i })
      .first()
      .click();
    await this.page
      .getByRole("button", { name: /GBP 55k to GBP 85k/i })
      .last()
      .click();
  }

  private async selectChip(
    _fieldLabel: string,
    optionLabel: string,
    occurrence = 0
  ): Promise<void> {
    await this.page
      .getByRole("button", { name: optionLabel, exact: true })
      .nth(occurrence)
      .click();
  }

  private async selectSearchOption(
    triggerLabel: string,
    optionLabel: string,
    multi = false
  ): Promise<void> {
    await this.page
      .getByRole("button", { name: triggerLabel, exact: true })
      .click();
    await this.page
      .locator('input[placeholder="Search…"]:visible')
      .last()
      .fill(optionLabel);
    await this.page
      .getByRole("button", { name: optionLabel, exact: true })
      .click();
    if (multi) {
      await this.page
        .getByRole("button", { name: "DONE", exact: true })
        .click();
    }
  }

  private async continueTo(nextStep: number): Promise<void> {
    await this.page.getByText("Continue", { exact: true }).click();
    await this.expectStep(nextStep);
  }

  private async expectStep(step: number, title?: string): Promise<void> {
    await expect(
      this.page.getByText(`STEP ${step} OF 13`, { exact: true })
    ).toBeVisible();
    if (title) {
      await expect(this.page.getByText(title, { exact: true })).toBeVisible();
    }
  }
}
