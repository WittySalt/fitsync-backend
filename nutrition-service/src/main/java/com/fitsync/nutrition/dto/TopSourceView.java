package com.fitsync.nutrition.dto;

import java.math.BigDecimal;

/** One food that contributed to a nutrient on a day, with how much it gave. */
public record TopSourceView(String foodName, BigDecimal amount) {
}
