package com.wroxtaaar.newfinance

import com.wroxtaaar.newfinance.data.Direction
import com.wroxtaaar.newfinance.parser.TransactionParser
import org.junit.Assert.*
import org.junit.Test
class TransactionParserTest{
 @Test fun debit(){val p=TransactionParser.parse("Rs. 1,250.00 debited from A/c XX1234 at SWIGGY. UTR 123456789012",1);assertNotNull(p);assertEquals(125000,p!!.amountMinor);assertEquals(Direction.DEBIT,p.direction);assertEquals("1234",p.accountLast4)}
 @Test fun credit(){val p=TransactionParser.parse("INR 500 credited to account XX9999. UTR ABC123456",1);assertNotNull(p);assertEquals(Direction.CREDIT,p!!.direction)}
 @Test fun otpRejected(){assertNull(TransactionParser.parse("Your OTP is 123456. Do not share it. Rs. 10 offer",1))}
 @Test fun promoRejected(){assertNull(TransactionParser.parse("Get Rs. 500 cashback offer today. Apply for loan",1))}
}
