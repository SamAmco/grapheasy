/*
 *  This file is part of Track & Graph
 *
 *  Track & Graph is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Track & Graph is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Track & Graph.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.samco.trackandgraph.statistics

import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.math.floor
import kotlin.math.round
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

class Statistics_getYParameters_KtTest {

    // these first four ones aren't really tests, but declaring them as tests makes it easier to run them

    @Test
    fun manuallyTestValuesNumerical() {
        runBlocking {
            val y_min = 39.2
            val y_max = 309.2

            printExampleNumerical(y_min.toDouble(), y_max.toDouble())
        }
    }

    @Test
    @ExperimentalTime
    fun manuallyTestValuesTime() {
        runBlocking {
            val y_min = 1 *60*60 + 53 *60 // in seconds
            val y_max = 8 *60*60 + 25 *60

            printExampleTime(y_min.toDouble(), y_max.toDouble())
        }
    }

    @Test
    @ExperimentalTime
    fun printExamplesTime() {
        val RANGE_USED_BELOW = 0.82
        for (q_ in 0 until 32) {

            for (qq_ in 0 until 200) {
                val start = Random.nextInt(0, 3 * 60 * 60).toDouble()
                val length = Random.nextInt(1, 10 * 60 * 60).toDouble()
                val end = start + length

                val interval = try {
                    getYParametersInternal(start, end, time_data = true, fixedBounds = false)
                } catch (e: Exception) {
                    PossibleInterval((end - start) / 10.0, false, 11, 1.0,
                            start, end, 1.0)
                }

                if (interval.percentage_range_used > RANGE_USED_BELOW) continue

                printExampleTime(start, end)
                break
            }
        }
    }

    @Test
    fun printExamplesNumerical() {
        val RANGE_USED_BELOW = 0.82
        for (q_ in 0 until 32) {

            for (qq_ in 0 until 200) {
                // generate new numbers until you get in the range_below regime. 201 tries should be enough.
                val start = Random.nextInt(0, 1000).toDouble() / 10
                val length = Random.nextInt(1, 500).toDouble() / 10
                val end = start + length

                val interval = try {
                    getYParametersInternal(start, end, time_data = false, fixedBounds = false)
                } catch (e: Exception) {
                    PossibleInterval((end - start) / 10.0, false, 11, 1.0,
                            start, end, 1.0)
                }

                if (interval.percentage_range_used > RANGE_USED_BELOW) continue

                printExampleNumerical(start, end)
                break
            }

        }
    }

    fun find_solution_for_everything(time_data: Boolean) {
        var errors = 0
        val allowed_error_percentage = when (time_data) {
            false -> 0.1 // it's ok if one   in 1000 doesn't have a good solution
            true  -> 0.7 // it's ok if seven in 1000 don't   have a good solution
        }
        val no_solution_vals = mutableListOf<Pair<Double, Double>>()
        val intervalList = mutableListOf<PossibleInterval>()

        val startValues  = when (time_data) {
            false -> (0..10000).map { it.toDouble()/10 }
            true  -> (0..36000).map { it.toDouble() } // zero seconds to 10 hours
        }
        val lengthValues = when (time_data) {
            false -> (1..7500).map { it.toDouble()/10 }
            true  -> (1..72000).map { it.toDouble() } // one second to 20 hours
        }

        for (start in startValues.shuffled().slice(0..1000)) {
            for (length in lengthValues.shuffled().slice(0..5000)) {
                try {
                    val interval = getYParametersInternal(start, start+length,
                            time_data = time_data,
                            fixedBounds = false)

                    val range_used = length / (interval.bounds_max-interval.bounds_min)
                    assert(range_used - interval.percentage_range_used < 0.001)
                    intervalList.add(interval)
                } catch (e: Exception) {
                    no_solution_vals.add(Pair(start, start+length))
                    //throw Exception("min: $start, max: ${start + length}")
                    errors += 1
                }

            }
        }

        val range_used_list = intervalList.map { it.percentage_range_used }
        print("minimum range used: ${range_used_list.minOrNull()}\n\n")
        val nRuns = range_used_list.count()
        for (i in 0..9) {
            val top = 1-i*0.03
            val bot = 1-(i+1)*0.03
            val nInRange = range_used_list.filter { top >= it && it > bot }.count()
            println("%.2f".format(top) + " -> " + "%.2f".format(bot)+
                    ":" + "%.2f".format(100*nInRange.toDouble()/nRuns.toDouble()).padStart(6, ' ') + "%")

        }
        println("Didn't find a good solution for ${100*errors.toDouble()/nRuns.toDouble()}%")
        //println("$no_solution_vals")

        println("How many lines are drawn how often:")
        intervalList
                .map { it.n_lines }.groupingBy { it }
                .eachCount().mapValues { 100*it.value.toDouble()/nRuns }
                .entries.sortedBy { it.key }
                .forEach { println("${it.key}:".padStart(3,' ') + "%.2f".format(it.value).padStart(6,' ')+ "%") }

        println("Which divisors are chosen how often:")
        intervalList
                .map { round(it.base/it.interval).toInt() }.groupingBy { it }
                .eachCount().mapValues { 100*it.value.toDouble()/nRuns }
                .entries.sortedBy { it.key }
                .forEach { println("${it.key}:".padStart(3, ' ') + "%.2f".format(it.value).padStart(6,' ')+ "%") }
        //assertEquals( 0, errors)
        println()
        print("Some of the combinations where the algorithm did not fine a good solution (start, end): ")
        println( no_solution_vals.slice(0..15) )
        assertTrue(100*errors.toDouble()/nRuns.toDouble() <= allowed_error_percentage)
    }

    @Test
    fun find_solution_for_everything_numerical() {
        runBlocking {
            find_solution_for_everything(time_data = false)
            /**
             * Output from 2021.02.19:
            minimum range used: 0.79

            1.00 -> 0.97:  5.04%
            0.97 -> 0.94: 13.95%
            0.94 -> 0.91: 22.18%
            0.91 -> 0.88: 28.73%
            0.88 -> 0.85: 22.78%
            0.85 -> 0.82:  5.51%
            0.82 -> 0.79:  1.80%
            0.79 -> 0.76:  0.00%
            0.76 -> 0.73:  0.00%
            0.73 -> 0.70:  0.00%
            Didn't find a good solution for 0.06582356483621137%
            How many lines are drawn how often [%]:
            6:  1.01%
            7:  5.64%
            8: 12.13%
            9: 13.81%
            10: 20.51%
            11: 25.88%
            12: 21.02%
            Which divisors are chosen how often [%]:
            1: 11.98%
            2: 45.47%
            3:  8.43%
            4: 13.54%
            5: 19.23%
            8:  1.35%
             */
        }
    }

    @Test
    fun find_solution_for_everything_time() {
        runBlocking {
            find_solution_for_everything(time_data = true)
            /**
             * Output from 2021.02.19:
            minimum range used: 0.79

            1.00 -> 0.97:  5.03%
            0.97 -> 0.94: 14.10%
            0.94 -> 0.91: 23.95%
            0.91 -> 0.88: 27.15%
            0.88 -> 0.85: 19.65%
            0.85 -> 0.82:  7.82%
            0.82 -> 0.79:  2.29%
            0.79 -> 0.76:  0.00%
            0.76 -> 0.73:  0.00%
            0.73 -> 0.70:  0.00%
            Didn't find a good solution for 0.5568272737700084%
            How many lines are drawn how often [%]:
            6:  0.41%
            7:  7.57%
            8: 15.76%
            9: 20.14%
            10: 22.12%
            11: 16.92%
            12: 17.08%
            Which divisors are chosen how often [%]:
            1: 38.29%
            2: 21.79%
            3: 11.06%
            4: 10.59%
            6:  9.92%
            12:  5.19%
            24:  1.28%
            30:  1.89%

             */
        }
    }

        /*
    private fun printExampleNumerical(start:Double, end: Double) {
        val interval = try {
            getYParametersInternal(start, end, time_data = false, fixedBounds = false)
        } catch (e: Exception) {
            PossibleInterval((end - start) / 10.0, false, 11, 1.0,
                    start, end, 1.0)
        }
        printExampleNumerical( start, end, interval)
    }
*/
    private fun printExampleNumerical(start: Double, end: Double) {
        val parameters = getYParameters(start, end, time_data = false, fixedBounds = false)
        val boundsRange = parameters.bounds_max - parameters.bounds_min
        val interval = boundsRange / (parameters.n_intervals-1)
        println("----------------------------")
        for (i in 0 until parameters.n_intervals.toInt()) {
            val label = parameters.bounds_max - i*interval
            println("$label")
        }
        print("Data range: [$start -> $end] ")
        print("interval: ${interval} x ${parameters.n_intervals} | ")
        println("range used = %.1f".format(100*(end-start)/boundsRange))
        //return interval
    }



    @ExperimentalTime
    private fun printExampleTime(start: Double, end: Double): YAxisParameters {
        val parameters = getYParameters(start, end, time_data = true, fixedBounds = false, throw_exc_if_non_found = false)
        val boundsRange = parameters.bounds_max - parameters.bounds_min
        val interval = boundsRange / (parameters.n_intervals-1)
        println("----------------------------")
        for (i in 0 until parameters.n_intervals.toInt()) {
            val label_seconds = parameters.bounds_max - i*interval
            val label = duration2string(label_seconds.seconds)
            println(label)
        }
        print("Data range: [${duration2string(start.seconds)} -> ${duration2string(end.seconds)}] ")
        print("interval: ${duration2string(interval.seconds)} x ${parameters.n_intervals.toInt()} | ")
        println("range used = ${round(100*(end-start)/boundsRange)}%")
        return parameters
    }

    @ExperimentalTime
    fun duration2string(duration: Duration): String {
        val hours = floor(duration.inHours).toInt().toString().padStart(2, '0')
        val mins = floor(duration.inMinutes).rem(60).toInt().toString().padStart(2, '0')
        val seconds = floor(duration.inSeconds).rem(60).toInt().toString().padStart(2, '0')

        return "$hours:$mins:$seconds"

    }
}