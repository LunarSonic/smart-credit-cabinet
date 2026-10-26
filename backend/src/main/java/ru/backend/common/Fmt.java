package ru.backend.common;

import java.math.BigDecimal;
import java.util.Locale;

public final class Fmt {
    private Fmt() {}
    public static String money(BigDecimal v) {
        return String.format(Locale.forLanguageTag("ru"), "%,.0f", v).replace('\u00a0', ' ').replace('\u202f', ' ') + " ₽";
    }
}
