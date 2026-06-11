package com.fitsync.persistence.code;

import jakarta.persistence.AttributeConverter;

public abstract class CodeEnumAttributeConverter<E extends Enum<E> & CodeEnum> implements AttributeConverter<E, String> {

    private final Class<E> enumType;

    protected CodeEnumAttributeConverter(Class<E> enumType) {
        this.enumType = enumType;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        return CodeEnum.fromCode(enumType, dbData);
    }
}
