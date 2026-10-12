package com.pesaflow.app.data.database

import androidx.room.TypeConverter
import com.pesaflow.app.data.models.*


class Converters {
    @TypeConverter
    fun fromTransactionType(type: TransactionType): String = type.name


    @TypeConverter
    fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)


    @TypeConverter
    fun fromTransactionSource(source: TransactionSource): String = source.name


    @TypeConverter
    fun toTransactionSource(value: String): TransactionSource = TransactionSource.valueOf(value)


    @TypeConverter
    fun fromPaymentMethod(method: PaymentMethod): String = method.name


    @TypeConverter
    fun toPaymentMethod(value: String): PaymentMethod = PaymentMethod.valueOf(value)


    @TypeConverter
    fun fromBudgetType(type: BudgetType): String = type.name


    @TypeConverter
    fun toBudgetType(value: String): BudgetType = BudgetType.valueOf(value)


    @TypeConverter
    fun fromAppLanguage(lang: AppLanguage): String = lang.name


    @TypeConverter
    fun toAppLanguage(value: String): AppLanguage = AppLanguage.valueOf(value)


    @TypeConverter
    fun fromTagList(tags: List<String>): String = tags.joinToString(separator = "|||")


    @TypeConverter
    fun toTagList(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split("|||")


    @TypeConverter
    fun fromPendingTransactionStatus(status: PendingTransactionStatus): Int = status.ordinal


    @TypeConverter
    fun toPendingTransactionStatus(ordinal: Int): PendingTransactionStatus =
        PendingTransactionStatus.values().getOrElse(ordinal) { PendingTransactionStatus.QUEUED }
}
