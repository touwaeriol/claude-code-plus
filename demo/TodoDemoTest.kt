package com.asakii.demo

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class SimpleCalculatorTest {

    private lateinit var calculator: SimpleCalculator

    @BeforeEach
    fun setup() {
        calculator = SimpleCalculator()
    }

    @Test
    fun `test addition`() {
        assertEquals(5.0, calculator.add(2.0, 3.0))
        assertEquals(0.0, calculator.add(-1.0, 1.0))
    }

    @Test
    fun `test subtraction`() {
        assertEquals(2.0, calculator.subtract(5.0, 3.0))
        assertEquals(-5.0, calculator.subtract(0.0, 5.0))
    }

    @Test
    fun `test multiplication`() {
        assertEquals(15.0, calculator.multiply(3.0, 5.0))
        assertEquals(0.0, calculator.multiply(0.0, 100.0))
    }

    @Test
    fun `test division`() {
        assertEquals(2.0, calculator.divide(10.0, 5.0))
        assertEquals(0.5, calculator.divide(1.0, 2.0))
    }

    @Test
    fun `test division by zero throws exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            calculator.divide(10.0, 0.0)
        }
    }

    @Test
    fun `test power`() {
        assertEquals(8.0, calculator.power(2.0, 3.0))
        assertEquals(1.0, calculator.power(5.0, 0.0))
    }
}