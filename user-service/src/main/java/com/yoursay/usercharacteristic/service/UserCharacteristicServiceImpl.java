package com.yoursay.usercharacteristic.service;

import com.yoursay.usercharacteristic.UserCharacteristicDto;
import com.yoursay.usercharacteristic.UserCharacteristicService;
import com.yoursay.usercharacteristic.model.Enums.CountryOfBirth;
import com.yoursay.usercharacteristic.model.Enums.EyeColor;
import com.yoursay.usercharacteristic.model.Enums.Height;
import com.yoursay.usercharacteristic.model.Enums.IncomeRange;
import com.yoursay.usercharacteristic.model.Enums.Parent;
import com.yoursay.usercharacteristic.model.Enums.PoliticalPersuasion;
import com.yoursay.usercharacteristic.model.Enums.Race;
import com.yoursay.usercharacteristic.model.Enums.SexAtBirth;
import com.yoursay.usercharacteristic.model.Enums.UKCounty;
import com.yoursay.usercharacteristic.model.Enums.UniversitySubject;
import com.yoursay.usercharacteristic.model.Enums.WeightRange;
import com.yoursay.usercharacteristic.model.UserCharacteristic;
import com.yoursay.usercharacteristic.model.UserCharacteristicRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class UserCharacteristicServiceImpl implements UserCharacteristicService {

    @Inject
    UserCharacteristicRepository characteristicRepository;

    @Override
    public UserCharacteristicDto getByUserId(long userId) {
        return toDto(characteristicRepository.getUserCharacteristicByUserId(userId));
    }

    @Override
    public UserCharacteristicDto save(UserCharacteristicDto characteristic) {
        return toDto(characteristicRepository.saveUserCharacteristic(toEntity(characteristic)));
    }

    private static UserCharacteristic toEntity(UserCharacteristicDto characteristic) {
        return new UserCharacteristic(
                characteristic.id(),
                characteristic.userId(),
                characteristic.postcode(),
                UKCounty.valueOf(characteristic.ukCounty()),
                Race.valueOf(characteristic.race()),
                IncomeRange.valueOf(characteristic.incomeRange()),
                CountryOfBirth.valueOf(characteristic.countryOfBirth()),
                PoliticalPersuasion.valueOf(characteristic.politicalPersuasion()),
                SexAtBirth.valueOf(characteristic.sexAtBirth()),
                Height.valueOf(characteristic.height()),
                EyeColor.valueOf(characteristic.eyeColor()),
                WeightRange.valueOf(characteristic.weightRange()),
                Parent.valueOf(characteristic.parent()),
                characteristic.universityEducated(),
                UniversitySubject.valueOf(characteristic.universitySubject()),
                characteristic.propertyOwner()
        );
    }

    private static UserCharacteristicDto toDto(UserCharacteristic characteristic) {
        if (characteristic == null) {
            return null;
        }
        return new UserCharacteristicDto(
                characteristic.getId(),
                characteristic.getUserId(),
                characteristic.getPostcode(),
                characteristic.getUkCounty().name(),
                characteristic.getRaceEnum().name(),
                characteristic.getIncomeRangeEnum().name(),
                characteristic.getCountryOfBirthEnum().name(),
                characteristic.getPoliticalPersuasionEnum().name(),
                characteristic.getSexAtBirthEnum().name(),
                characteristic.getHeightEnum().name(),
                characteristic.getEyeColorEnum().name(),
                characteristic.getWeightRangeEnum().name(),
                characteristic.getParentEnum().name(),
                characteristic.isUniversityEducated(),
                characteristic.getUniversitySubjectEnum().name(),
                characteristic.isPropertyOwner()
        );
    }
}
