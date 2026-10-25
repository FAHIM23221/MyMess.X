package com.messerp.app.data

import org.json.JSONArray
import org.json.JSONObject

data class OffPeriod(
    val id: String,
    val from: String,
    val to: String,
    val breakfast: Boolean,
    val lunch: Boolean,
    val dinner: Boolean,
    val reason: String?
)

data class MealStatus(
    val today: String,
    val breakfast: Boolean,
    val lunch: Boolean,
    val dinner: Boolean,
    val periods: List<OffPeriod>
)

data class MemberMeal(
    val id: String,
    val name: String,
    val code: String,
    val meals: Long,
    val guestMeals: Long,
    val costMinor: Long
)

data class MealSummary(
    val monthStart: String,
    val through: String,
    val totalExpenseMinor: Long,
    val totalMeals: Long,
    val rateMinor: String?,
    val members: List<MemberMeal>
)

data class Expense(
    val id: String,
    val date: String,
    val category: String,
    val amountMinor: Long,
    val description: String,
    val voided: Boolean,
    val voidReason: String?
)

data class GuestMeal(
    val id: String,
    val memberId: String,
    val date: String,
    val slot: String,
    val count: Int
)

fun parseOff(o: JSONObject) = OffPeriod(
    id = o.getString("id"),
    from = o.getString("from_date"),
    to = o.getString("to_date"),
    breakfast = o.getBoolean("off_breakfast"),
    lunch = o.getBoolean("off_lunch"),
    dinner = o.getBoolean("off_dinner"),
    reason = o.strOrNull("reason")
)

fun parseMealStatus(o: JSONObject): MealStatus {
    val arr = o.getJSONArray("periods")
    return MealStatus(
        today = o.getString("today"),
        breakfast = o.getBoolean("breakfast"),
        lunch = o.getBoolean("lunch"),
        dinner = o.getBoolean("dinner"),
        periods = (0 until arr.length()).map { parseOff(arr.getJSONObject(it)) }
    )
}

fun parseMealSummary(o: JSONObject): MealSummary {
    val arr: JSONArray = o.getJSONArray("members")
    return MealSummary(
        monthStart = o.getString("month_start"),
        through = o.getString("through"),
        totalExpenseMinor = o.getLong("total_expense_minor"),
        totalMeals = o.getLong("total_meals"),
        rateMinor = o.strOrNull("rate_minor"),
        members = (0 until arr.length()).map {
            val m = arr.getJSONObject(it)
            MemberMeal(
                id = m.getString("id"),
                name = m.getString("full_name"),
                code = m.getString("member_code"),
                meals = m.getLong("meals"),
                guestMeals = m.getLong("guest_meals"),
                costMinor = m.getLong("cost_minor")
            )
        }
    )
}

fun parseExpense(o: JSONObject) = Expense(
    id = o.getString("id"),
    date = o.getString("expense_date"),
    category = o.getString("category"),
    amountMinor = o.getLong("amount_minor"),
    description = o.getString("description"),
    voided = o.getString("status") == "VOIDED",
    voidReason = o.strOrNull("void_reason")
)

fun parseGuestMeal(o: JSONObject) = GuestMeal(
    id = o.getString("id"),
    memberId = o.getString("member_id"),
    date = o.getString("meal_date"),
    slot = o.getString("slot"),
    count = o.getInt("guests_count")
)
