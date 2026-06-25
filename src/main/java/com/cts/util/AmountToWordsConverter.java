package com.cts.util;

import java.math.BigDecimal;

public class AmountToWordsConverter {

    private static final String[] ONES = {
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven",
        "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen",
        "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"
    };
    private static final String[] TENS = {
        "", "", "Twenty", "Thirty", "Forty", "Fifty",
        "Sixty", "Seventy", "Eighty", "Ninety"
    };

    public static String convert(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) return "";
        long rupees = amount.longValue();
        int  paise  = amount.remainder(BigDecimal.ONE)
                            .multiply(new BigDecimal("100")).intValue();
        if (rupees == 0 && paise == 0) return "Zero Only";
        StringBuilder sb = new StringBuilder();
        if (rupees > 0) sb.append(inWords(rupees));
        if (paise  > 0) { if (sb.length() > 0) sb.append(" and "); sb.append("Paise ").append(inWords(paise)); }
        sb.append(" Only");
        return sb.toString().trim();
    }

    private static String inWords(long n) {
        if (n == 0)         return "";
        if (n < 20)         return ONES[(int) n];
        if (n < 100)        return TENS[(int)(n/10)] + (n%10>0 ? " "+ONES[(int)(n%10)] : "");
        if (n < 1000)       return ONES[(int)(n/100)] + " Hundred" + (n%100>0 ? " "+inWords(n%100) : "");
        if (n < 100000)     return inWords(n/1000)    + " Thousand" + (n%1000>0   ? " "+inWords(n%1000)   : "");
        if (n < 10000000)   return inWords(n/100000)  + " Lakh"     + (n%100000>0 ? " "+inWords(n%100000) : "");
        return                     inWords(n/10000000)+ " Crore"    + (n%10000000>0? " "+inWords(n%10000000):"");
    }
}