"""Generate Order_Complete_Logic.pdf — boss-ready handout."""

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import cm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    HRFlowable,
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)


# ---------- Register Unicode-capable fonts (Arial on Windows) so em-dashes,
#            smart quotes, bullets, and math symbols extract & copy-paste cleanly.
WIN_FONTS = r"C:\Windows\Fonts"
pdfmetrics.registerFont(TTFont("UIBody",      f"{WIN_FONTS}\\arial.ttf"))
pdfmetrics.registerFont(TTFont("UIBody-Bold", f"{WIN_FONTS}\\arialbd.ttf"))
pdfmetrics.registerFont(TTFont("UIMono",      f"{WIN_FONTS}\\consola.ttf"))
pdfmetrics.registerFontFamily(
    "UIBody", normal="UIBody", bold="UIBody-Bold", italic="UIBody", boldItalic="UIBody-Bold"
)


# ---------- Colors (Mehrgo brand) ----------
BRAND = colors.HexColor("#F58320")
INK = colors.HexColor("#0A0E14")
SUBINK = colors.HexColor("#5B6573")
HAIRLINE = colors.HexColor("#E5E8EB")
CODE_BG = colors.HexColor("#F2F4F7")
ROW_ALT = colors.HexColor("#F7F8FA")
DANGER = colors.HexColor("#DC2626")


# ---------- Styles ----------
styles = getSampleStyleSheet()

H1 = ParagraphStyle(
    "H1",
    parent=styles["Heading1"],
    fontName="UIBody-Bold",
    fontSize=22,
    leading=26,
    textColor=INK,
    spaceBefore=0,
    spaceAfter=4,
)

SUBTITLE = ParagraphStyle(
    "Subtitle",
    parent=styles["Normal"],
    fontName="UIBody",
    fontSize=11,
    leading=14,
    textColor=SUBINK,
    spaceAfter=16,
)

H2 = ParagraphStyle(
    "H2",
    parent=styles["Heading2"],
    fontName="UIBody-Bold",
    fontSize=14,
    leading=18,
    textColor=BRAND,
    spaceBefore=16,
    spaceAfter=4,
)

H3 = ParagraphStyle(
    "H3",
    parent=styles["Heading3"],
    fontName="UIBody-Bold",
    fontSize=11,
    leading=14,
    textColor=INK,
    spaceBefore=10,
    spaceAfter=2,
)

BODY = ParagraphStyle(
    "Body",
    parent=styles["Normal"],
    fontName="UIBody",
    fontSize=10,
    leading=14,
    textColor=INK,
    spaceAfter=6,
    alignment=TA_LEFT,
)

BULLET = ParagraphStyle(
    "Bullet",
    parent=BODY,
    leftIndent=14,
    bulletIndent=2,
    spaceAfter=2,
)

NOTE = ParagraphStyle(
    "Note",
    parent=BODY,
    fontSize=9,
    leading=12,
    textColor=SUBINK,
    leftIndent=10,
    borderColor=BRAND,
    borderWidth=0,
    borderPadding=0,
    spaceBefore=4,
    spaceAfter=8,
)

CODE = ParagraphStyle(
    "Code",
    parent=styles["Code"],
    fontName="UIMono",
    fontSize=8.5,
    leading=11,
    textColor=INK,
    backColor=CODE_BG,
    borderColor=HAIRLINE,
    borderWidth=0.5,
    borderPadding=8,
    leftIndent=0,
    rightIndent=0,
    spaceBefore=4,
    spaceAfter=10,
)

TABLE_HEAD_STYLE = ParagraphStyle(
    "THead",
    parent=BODY,
    fontName="UIBody-Bold",
    fontSize=9.5,
    leading=12,
    textColor=colors.white,
    spaceAfter=0,
)

TABLE_CELL_STYLE = ParagraphStyle(
    "TCell",
    parent=BODY,
    fontName="UIBody",
    fontSize=9.5,
    leading=12,
    spaceAfter=0,
)

TABLE_CELL_MONO = ParagraphStyle(
    "TCellMono",
    parent=TABLE_CELL_STYLE,
    fontName="UIMono",
    fontSize=8.5,
    leading=11,
)


def p(text, style=BODY):
    return Paragraph(text, style)


def cell(text, mono=False):
    return Paragraph(text, TABLE_CELL_MONO if mono else TABLE_CELL_STYLE)


def head_cell(text):
    return Paragraph(text, TABLE_HEAD_STYLE)


def table(data, col_widths, header_bg=BRAND):
    """Render a table with branded header and zebra rows."""
    tbl = Table(data, colWidths=col_widths, repeatRows=1)
    style = [
        ("BACKGROUND", (0, 0), (-1, 0), header_bg),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("FONTNAME", (0, 0), (-1, 0), "UIBody-Bold"),
        ("ALIGN", (0, 0), (-1, -1), "LEFT"),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("INNERGRID", (0, 0), (-1, -1), 0.25, HAIRLINE),
        ("BOX", (0, 0), (-1, -1), 0.5, HAIRLINE),
        ("LEFTPADDING", (0, 0), (-1, -1), 8),
        ("RIGHTPADDING", (0, 0), (-1, -1), 8),
        ("TOPPADDING", (0, 0), (-1, -1), 6),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
    ]
    for r in range(1, len(data)):
        if r % 2 == 0:
            style.append(("BACKGROUND", (0, r), (-1, r), ROW_ALT))
    tbl.setStyle(TableStyle(style))
    return tbl


def code(text):
    """Render a code block. Escapes < > and converts newlines."""
    escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    escaped = escaped.replace(" ", "&nbsp;").replace("\n", "<br/>")
    return Paragraph(escaped, CODE)


def rule():
    return HRFlowable(width="100%", thickness=0.5, color=HAIRLINE, spaceBefore=8, spaceAfter=8)


# ---------- Document ----------
def build():
    doc = SimpleDocTemplate(
        "Order_Complete_Logic.pdf",
        pagesize=A4,
        leftMargin=1.8 * cm,
        rightMargin=1.8 * cm,
        topMargin=1.6 * cm,
        bottomMargin=1.6 * cm,
        title="Mehrgo Driver — Order Complete Logic",
        author="TeamWork",
    )

    flow = []

    # ---------- Title block ----------
    flow.append(p("Mehrgo Driver — Order Complete Logic", H1))
    flow.append(
        p(
            "End-to-end flow of how the driver app finalises a trip and posts the fare. "
            "Source: Android app, branch <font face='UIMono'>mehrgo_driver_app</font>, "
            "version 1.9.1 (build 47).",
            SUBTITLE,
        )
    )

    # ---------- 1. Trigger ----------
    flow.append(p("1. Trigger", H2))
    flow.append(
        p(
            "The driver is in an active trip "
            "(<font face='UIMono'>OrderState.STARTED = 7</font>) and taps "
            "<b>“Finish trip”</b>. This calls "
            "<font face='UIMono'>prepareOrderFinish()</font> in "
            "<font face='UIMono'>MapFragment.kt:1364</font>.",
        )
    )

    # ---------- 2. Pre-finish refresh ----------
    flow.append(p("2. Pre-finish refresh", H2))
    flow.append(
        p(
            "Before showing the fare summary, the app refreshes the user from "
            "<font face='UIMono'>GET&nbsp;user/me</font> "
            "(<font face='UIMono'>MapFragment.kt:1366</font>) to pull:"
        )
    )
    flow.append(p("•&nbsp; the latest active order (<font face='UIMono'>user.orders[0]</font>)", BULLET))
    flow.append(
        p(
            "•&nbsp; the client’s current bonus balance &amp; bonus settings "
            "(so the deduction math is correct against server state)",
            BULLET,
        )
    )
    flow.append(
        p(
            "Then <font face='UIMono'>totalServicePrice</font> is summed from "
            "<font face='UIMono'>order.services[].total</font> "
            "(<font face='UIMono'>MapFragment.kt:1384</font>) and "
            "<font face='UIMono'>calculateTotalPrice()</font> runs."
        )
    )

    # ---------- 3. Fare computation ----------
    flow.append(p("3. Fare computation (local taximeter)", H2))
    flow.append(
        p(
            "Inside <font face='UIMono'>setViewDialogTrackingFinish()</font> "
            "(<font face='UIMono'>MapFragment.kt:1422</font>) — runs every time the summary dialog opens. "
            "The total fare is built in this order:"
        )
    )

    fare_rows = [
        [head_cell("Component"), head_cell("Source / Formula")],
        [cell("startingPrice", mono=True), cell("<font face='UIMono'>order.startingPrice</font> — base fare / minimum")],
        [cell("additionalPrice", mono=True), cell("<font face='UIMono'>order.addPrice</font>")],
        [cell("totalServicePrice", mono=True), cell("Sum of <font face='UIMono'>order.services[].total</font>")],
        [
            cell("totalWaitingPrice", mono=True),
            cell(
                "Accrued from <font face='UIMono'>MyTrackingService</font> while speed &lt; 7&nbsp;km/h, "
                "split into pre-arrival vs on-way at the tariff’s "
                "<font face='UIMono'>priceOfWaiting</font> / "
                "<font face='UIMono'>priceOfWaitingOnWay</font> rates"
            ),
        ],
        [
            cell("totalTrackingPriceInCity", mono=True),
            cell(
                "km inside <font face='UIMono'>branch.polygon</font> &#215; "
                "<font face='UIMono'>priceInCity</font> "
                "(or <font face='UIMono'>tariff.distanceIntervals</font> ladder)"
            ),
        ],
        [
            cell("totalTrackingPriceOutCity", mono=True),
            cell("km outside polygon &#215; <font face='UIMono'>tariff.priceOfOut</font>"),
        ],
    ]
    flow.append(table(fare_rows, col_widths=[5.2 * cm, 11.6 * cm]))

    flow.append(Spacer(1, 6))
    flow.append(
        p(
            "<b>totalPrice = startingPrice + additionalPrice + services + waiting + insideKm + outsideKm</b> "
            "<font color='#5B6573'>(MapFragment.kt:1448)</font>"
        )
    )

    # ---------- 4. Multi-stop shortcut guard ----------
    flow.append(p("4. Multi-stop “shortcut” guard", H2))
    flow.append(
        p(
            "For orders with <b>&gt;= 2 destinations</b> "
            "(<font face='UIMono'>MapFragment.kt:1447–1485</font>) the app picks between two prices:"
        )
    )

    flow.append(
        code(
            "distanceTracked          = distanceInCity + distanceOutCity   // what the driver actually drove\n"
            "distanceBetweenLocations = order.distance * 1000              // server's planned route distance\n"
            "distanceWay              = distance(lastFix -> order.locations.last())\n"
            "\n"
            "if |distanceTracked - distanceBetweenLocations| > 1000 m   OR   distanceWay < 200 m:\n"
            "        use the locally calculated taximeter total\n"
            "else:\n"
            "        use order.price (server-precomputed) + totalWaitingPrice"
        )
    )

    flow.append(
        p(
            "<b>Plain English.</b> If the driver clearly drove the planned route (within 1&nbsp;km of the "
            "planned distance <i>and</i> ended within 200&nbsp;m of the last drop-off), bill the agreed price. "
            "If they detoured or stopped short, bill the taximeter. This stops drivers from gaming the route, "
            "and also stops them being penalised when traffic forces a detour the taximeter would over-bill.",
            NOTE,
        )
    )

    # ---------- 5. Promo code ----------
    flow.append(p("5. Promo code deduction", H2))
    flow.append(p("Source: <font face='UIMono'>MapFragment.kt:1494</font>."))
    flow.append(
        p(
            "•&nbsp; <font face='UIMono'>order.promoCode.usage.amount</font> can be either "
            "<font face='UIMono'>\"10%\"</font> (percentage) or a flat number "
            "<font face='UIMono'>\"5000\"</font>.",
            BULLET,
        )
    )
    flow.append(
        p(
            "•&nbsp; Percentage: <font face='UIMono'>calculatedPromo = totalPrice / 100 &#215; percent</font>",
            BULLET,
        )
    )
    flow.append(
        p(
            "•&nbsp; Fixed: <font face='UIMono'>calculatedPromo = min(amount, totalPrice)</font>",
            BULLET,
        )
    )

    # ---------- 6. Bonus deduction ----------
    flow.append(p("6. Bonus deduction", H2))
    flow.append(
        p(
            "Source: <font face='UIMono'>MapFragment.kt:1524</font>. Only applies if "
            "<font face='UIMono'>order.useBonus == true</font>. "
            "Bonus cap (<font face='UIMono'>maxAmount</font>) is either a percent or a flat number, "
            "applied to <font face='UIMono'>(totalPrice - promo)</font>. Then:"
        )
    )
    flow.append(
        code(
            "calculatedBonusFinal = min(calculatedBonus, clientTotalBonus)\n"
            "if clientTotalBonus < minAmount:\n"
            "        calculatedBonusFinal = 0"
        )
    )
    flow.append(
        p(
            "So the bonus is bounded by <b>three</b> things: the tariff cap, what the client actually has, "
            "and the minimum threshold to use any bonus at all."
        )
    )

    # ---------- 7. Final amount ----------
    flow.append(p("7. Final amount shown to driver", H2))
    flow.append(
        p(
            "<font face='UIMono'>finalTotalPrice = totalPrice - calculatedBonusFinal - calculatedPromo</font>"
        )
    )
    flow.append(
        p(
            "The driver sees the <b>gross total</b>, the bonus deduction, the promo deduction, and the "
            "<b>final amount to collect from the client</b>."
        )
    )

    flow.append(PageBreak())

    # ---------- 8. Submit ----------
    flow.append(p("8. Submit", H2))
    flow.append(p("Source: <font face='UIMono'>MapFragment.kt:1568–1581</font>."))
    flow.append(
        code(
            "POST order/complete?order_id={orderId}\n"
            "\n"
            "Body (RequestOrderFinish):\n"
            "{\n"
            "  \"distance\":           \"{km}\",                  // rounded km as string\n"
            "  \"latitude_finish\":    {last GPS lat},          // MyTrackingService.lastLatLngWholeApp\n"
            "  \"longitude_finish\":   {last GPS lon},\n"
            "  \"total_price\":        \"{totalPrice}\",           // pre-deduction gross\n"
            "  \"waiting_time\":       \"{ms}\",                   // currentWaitTimeInMillis\n"
            "  \"execution_time\":     \"{ms}\",                   // currentTimeInMillis (total trip)\n"
            "  \"finish_address_id\":  null,                    // always null in current build\n"
            "  \"bonus_payment\":      {calculatedBonusFinal},  // debit from client's bonus wallet\n"
            "  \"promo_code_payment\": {calculatedPromo}        // debit from promo bucket\n"
            "}"
        )
    )
    flow.append(
        p(
            "Server returns <font face='UIMono'>BaseResponse&lt;User&gt;</font> — the refreshed user "
            "with new balance and cleared active orders."
        )
    )

    # ---------- 9. Success side effects ----------
    flow.append(p("9. Success side effects", H2))
    flow.append(p("Source: <font face='UIMono'>MapFragment.kt:1597–1612</font>."))
    for line in [
        "<b>UserManager.saveUser(...)</b> — local cache updated with new balance.",
        "<b>commandStopTracking()</b> — sends <font face='UIMono'>ACTION_STOP_TRACKING</font> intent to "
        "<font face='UIMono'>MyTrackingService</font>: stops GPS, location-upload websocket, and clears taximeter counters.",
        "<b>stopRouteService()</b> — clears the active-trip route line and stops route polling.",
        "Both dialogs dismissed (<font face='UIMono'>dialogTrackingFinish</font>, <font face='UIMono'>dialogFinishDestination</font>).",
        "Map state reset: <font face='UIMono'>destinationLocation = null</font>, placemarks + polyline cleared.",
        "<font face='UIMono'>findNavController().navigate(R.id.action_mapFragment_self)</font> — reloads "
        "the map fragment so the UI is back to idle / online.",
    ]:
        flow.append(p(f"•&nbsp; {line}", BULLET))

    # ---------- 10. Error handling ----------
    flow.append(p("10. Error handling", H2))
    flow.append(p("Source: <font face='UIMono'>MapFragment.kt:1614–1639</font>. Two error paths:"))

    err_rows = [
        [head_cell("Server response"), head_cell("App behaviour")],
        [
            cell("HTTP 402 (<font face='UIMono'>status: 402</font>)", mono=False),
            cell(
                "Show <font face='UIMono'>DialogPaymentError</font> with the server message — "
                "typically “client cannot pay this amount with bonus / card”. "
                "Driver collects in cash. <b>No retry.</b>"
            ),
        ],
        [
            cell("Other 4xx with <font face='UIMono'>ErrorOrderFinishResponse</font> payload"),
            cell(
                "Server is rejecting the bonus calculation. The payload carries the <b>correct</b> "
                "<font face='UIMono'>clientTotalBonus</font>, "
                "<font face='UIMono'>minAmount</font>, "
                "<font face='UIMono'>maxAmount</font>. "
                "App updates those values and re-renders the summary dialog so the driver can "
                "resubmit with the corrected bonus."
            ),
        ],
    ]
    flow.append(table(err_rows, col_widths=[5.2 * cm, 11.6 * cm]))

    flow.append(Spacer(1, 6))
    flow.append(
        p(
            "<b>Why duplicate the bonus math client + server?</b> "
            "The server is authoritative, but the client computes locally so the driver sees the price "
            "live while driving. On mismatch, the server quietly hands back the corrected settings and "
            "the client retries — the driver never sees a hard error.",
            NOTE,
        )
    )

    # ---------- Appendix: files touched ----------
    flow.append(rule())
    flow.append(p("Files involved", H3))
    file_rows = [
        [head_cell("Layer"), head_cell("File")],
        [cell("API"), cell("<font face='UIMono'>data/remote/ApiService.kt</font> — line 212")],
        [cell("Repository"), cell("<font face='UIMono'>data/repository/MainRepositoryImpl.kt</font> — line 81")],
        [cell("Use case"), cell("<font face='UIMono'>domain/use_case/main/OrderFinishUC.kt</font>")],
        [cell("Request DTO"), cell("<font face='UIMono'>domain/model/requests/RequestOrderFinish.kt</font>")],
        [cell("Error DTO"), cell("<font face='UIMono'>domain/model/error/ErrorOrderFinishResponse.kt</font>")],
        [cell("ViewModel"), cell("<font face='UIMono'>presentation/maps/yandex_map/MapViewModel.kt</font> — line 65")],
        [cell("UI / orchestration"), cell("<font face='UIMono'>presentation/maps/yandex_map/MapFragment.kt</font> — lines 1364–1643")],
    ]
    flow.append(table(file_rows, col_widths=[3.6 * cm, 13.2 * cm]))

    flow.append(Spacer(1, 12))
    flow.append(
        p(
            "Document generated for internal review. Aligned with Android branch "
            "<font face='UIMono'>mehrgo_driver_app</font>, commit "
            "<font face='UIMono'>3e7291a6</font>. "
            "Update this document if the <font face='UIMono'>RequestOrderFinish</font> schema or "
            "the multi-stop guard thresholds change.",
            NOTE,
        )
    )

    doc.build(flow)
    print("Wrote Order_Complete_Logic.pdf")


if __name__ == "__main__":
    build()
