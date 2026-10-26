package ru.backend.product.entity.enums;

public enum Purpose {
    EQUIPMENT("Покупка оборудования"),
    WORKING_CAPITAL("Пополнение оборотных средств"),
    CASH_GAP("Кассовый разрыв"),
    REAL_ESTATE("Коммерческая недвижимость"),
    REFINANCING("Рефинансирование"),
    OTHER("Другое");

    private final String label;
    Purpose(String label) { this.label = label; }
    public String label() { return label; }
}
