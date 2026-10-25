package com.messerp.app.data

import org.json.JSONObject

data class LedgerEntry(
    val id: String,
    val kind: String,
    val category: String,
    val amountMinor: Long,
    val description: String?,
    val entryDate: String,
    val paymentId: String?,
    val receiptNo: String?,
    val reversed: Boolean
)

data class MemberLedger(
    val monthStart: String,
    val openingMinor: Long,
    val chargesMinor: Long,
    val paymentsMinor: Long,
    val closingMinor: Long,
    val balanceNowMinor: Long,
    val dueMinor: Long,
    val advanceMinor: Long,
    val entries: List<LedgerEntry>
)

data class MemberBalance(val id: String, val name: String, val code: String, val balanceMinor: Long)

data class DuesOverview(val totalDueMinor: Long, val totalAdvanceMinor: Long, val members: List<MemberBalance>)

data class PaymentRow(
    val id: String,
    val memberId: String,
    val amountMinor: Long,
    val method: String,
    val paidDate: String,
    val receiptNo: String,
    val status: String
)

data class Receipt(
    val receiptNo: String,
    val status: String,
    val amountMinor: Long,
    val method: String,
    val paidDate: String,
    val note: String?,
    val txnRef: String?,
    val createdAt: String,
    val createdLocal: String,
    val reversedLocal: String?,
    val memberName: String,
    val memberCode: String,
    val memberPhone: String,
    val receivedBy: String?,
    val messName: String,
    val reversalReason: String?
)

fun parseLedger(o: JSONObject): MemberLedger {
    val arr = o.getJSONArray("entries")
    return MemberLedger(
        monthStart = o.getString("month_start"),
        openingMinor = o.getLong("opening_minor"),
        chargesMinor = o.getLong("charges_minor"),
        paymentsMinor = o.getLong("payments_minor"),
        closingMinor = o.getLong("closing_minor"),
        balanceNowMinor = o.getLong("balance_now_minor"),
        dueMinor = o.getLong("due_minor"),
        advanceMinor = o.getLong("advance_minor"),
        entries = (0 until arr.length()).map {
            val e = arr.getJSONObject(it)
            LedgerEntry(
                id = e.getString("id"),
                kind = e.getString("kind"),
                category = e.getString("category"),
                amountMinor = e.getLong("amount_minor"),
                description = e.strOrNull("description"),
                entryDate = e.getString("entry_date"),
                paymentId = e.strOrNull("payment_id"),
                receiptNo = e.strOrNull("receipt_no"),
                reversed = e.optBoolean("reversed", false)
            )
        }
    )
}

fun parseDues(o: JSONObject): DuesOverview {
    val arr = o.getJSONArray("members")
    return DuesOverview(
        totalDueMinor = o.getLong("total_due_minor"),
        totalAdvanceMinor = o.getLong("total_advance_minor"),
        members = (0 until arr.length()).map {
            val m = arr.getJSONObject(it)
            MemberBalance(m.getString("id"), m.getString("full_name"), m.getString("member_code"), m.getLong("balance_minor"))
        }
    )
}

fun parsePayment(o: JSONObject) = PaymentRow(
    id = o.getString("id"),
    memberId = o.getString("member_id"),
    amountMinor = o.getLong("amount_minor"),
    method = o.getString("method"),
    paidDate = o.getString("paid_date"),
    receiptNo = o.getString("receipt_no"),
    status = o.getString("status")
)

fun parseReceipt(o: JSONObject) = Receipt(
    receiptNo = o.getString("receipt_no"),
    status = o.getString("status"),
    amountMinor = o.getLong("amount_minor"),
    method = o.getString("method"),
    paidDate = o.getString("paid_date"),
    note = o.strOrNull("note"),
    txnRef = o.strOrNull("txn_ref"),
    createdAt = o.getString("created_at"),
    createdLocal = o.getString("created_local"),
    reversedLocal = o.strOrNull("reversed_local"),
    memberName = o.getString("member_name"),
    memberCode = o.getString("member_code"),
    memberPhone = o.getString("member_phone"),
    receivedBy = o.strOrNull("received_by"),
    messName = o.getString("mess_name"),
    reversalReason = o.strOrNull("reversal_reason")
)
