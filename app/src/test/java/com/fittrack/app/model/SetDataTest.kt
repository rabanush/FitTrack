package com.fittrack.app.model

import com.fittrack.app.viewmodel.SetData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetDataTest {

    @Test
    fun defaultSetData_isNotCompleted() {
        val set = SetData(setNumber = 1)
        assertFalse(set.isCompleted)
        assertEquals("", set.weight)
        assertEquals("", set.reps)
        assertEquals("0", set.rir)
    }

    @Test
    fun setData_copyPreservesFields() {
        val set = SetData(
            setNumber = 2,
            weight = "80",
            reps = "10",
            rir = "2",
            isCompleted = true,
            prevWeight = "75",
            prevReps = "8",
            prevRir = "3"
        )
        val copy = set.copy(weight = "85")
        assertEquals(85.0, copy.weight.toDouble(), 0.0)
        assertEquals("10", copy.reps)
        assertTrue(copy.isCompleted)
        assertEquals("75", copy.prevWeight)
    }

    @Test
    fun setData_equalityBasedOnAllFields() {
        val a = SetData(setNumber = 1, weight = "60", reps = "8")
        val b = SetData(setNumber = 1, weight = "60", reps = "8")
        val c = SetData(setNumber = 1, weight = "70", reps = "8")
        assertEquals(a, b)
        assertFalse(a == c)
    }

    @Test
    fun setData_hashCodeConsistentWithEquals() {
        val a = SetData(setNumber = 3, weight = "100", reps = "5")
        val b = SetData(setNumber = 3, weight = "100", reps = "5")
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun setData_prevFieldsDefaultToEmpty() {
        val set = SetData(setNumber = 1)
        assertEquals("", set.prevWeight)
        assertEquals("", set.prevReps)
        assertEquals("", set.prevRir)
    }

    @Test
    fun setData_isImmutableDataClass() {
        val set = SetData(setNumber = 1, weight = "50", reps = "12")
        val modified = set.copy(isCompleted = true)
        assertFalse(set.isCompleted)
        assertTrue(modified.isCompleted)
    }
}
