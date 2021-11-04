package com.samco.trackandgraph.antlr.evaluation

import com.samco.trackandgraph.R
import com.samco.trackandgraph.database.entity.DataPointInterface

fun callFunction(functionName: String, args: List<Value>) : Value {
//    FunctionCallableFromCode::class.sealedSubclasses.map { it.objectInstance?.functionName  }
    return when(functionName) {
        "Delta" -> DeltaFunction().main(args)
        "Accumulate" -> AccumulateFunction().main(args)
        "Derivative" -> DerivativeFunction().main(args)
        "TimeBetween" -> TimeBetweenFunction().main(args)
        "TimeBetween2" -> TimeBetween2Function().main(args)

        "Filter" -> FilterFunction().main(args)
        "Exclude" -> ExcludeFunction().main(args)
        else -> throw UnknownFunctionName(functionName)
    }
}

sealed class FunctionCallableFromCode(val functionName: String, val signature: Int) {
    /**
     * Throws errors if the argument has the wrong type or is missing.
     */
    inline fun <reified T : Value> getArgument(
        argumentList: List<Value>,
        index: Int
    ): T {
        val arg =
            argumentList.elementAtOrNull(index) ?: throw ArgMissingError(functionName, index, T::class)
        when (arg) {
            is T -> return arg
            else -> throw WrongArgDatatypeError(functionName, index, arg::class, listOf(T::class))
        }
    }

    fun checkTooManyArgs(argumentList: List<Value>, expectedNumber: Number) {
        if (argumentList.size != expectedNumber) {
            throw TooManyArgsError(functionName, expectedNumber, argumentList.size)
        }
    }

    abstract fun main(args: List<Value>) : Value

}

class DeltaFunction : FunctionCallableFromCode("Delta", R.string.functionsig_delta) {
    override fun main(args: List<Value>) : Value {
        val inputData = getArgument<DatapointsValue>(args, 0)
        inputData.confirmType(listOf(DataType.NUMERICAL, DataType.TIME))
        checkTooManyArgs(args, 1)

        val outputData = inputData.datapoints.zipWithNext { a, b -> b.copy(value = b.value - a.value) }
        return DatapointsValue(outputData, inputData.dataType, inputData.regularity)
    }
}

class AccumulateFunction : FunctionCallableFromCode("Accumulate", R.string.functionsig_accumulate) {
    override fun main(args: List<Value>): Value {
        val inputData = getArgument<DatapointsValue>(args, 0)
        inputData.confirmType(listOf(DataType.NUMERICAL, DataType.TIME))
        checkTooManyArgs(args, 1)

        val outputData = mutableListOf<DataPointInterface>()
        var sum = 0.0
        for (i in inputData.datapoints.indices) {
            val point = inputData.datapoints[i]
            sum += point.value
            outputData.add(point.copy(value = sum))
        }

        return DatapointsValue(outputData, inputData.dataType, inputData.regularity)
    }
}

class DerivativeFunction : FunctionCallableFromCode("Derivative", R.string.functionsig_derivative) {
    override fun main(args: List<Value>): Value {
        val inputData = getArgument<DatapointsValue>(args, 0)
        inputData.confirmType(listOf(DataType.NUMERICAL, DataType.TIME))
        val perTime = getArgument<TimeValue>(args, 1)
        checkTooManyArgs(args, 2)

        fun calcDerivative(a: DataPointInterface, b: DataPointInterface) : DataPointInterface {
            val diff = b.value - a.value
            val derivative = diff / (b.timestamp.toEpochSecond() - a.timestamp.toEpochSecond())
            val scaledDerivative = derivative * perTime.duration.seconds
            return b.copy(value = scaledDerivative)
        }

        val outputData = inputData.datapoints.zipWithNext { a, b -> calcDerivative(a, b) }
        return DatapointsValue(outputData, inputData.dataType, inputData.regularity)
    }
}

class TimeBetweenFunction : FunctionCallableFromCode("TimeBetween", R.string.functionsig_timebetween) {
    override fun main(args: List<Value>): Value {
        val inputData = getArgument<DatapointsValue>(args, 0)
        checkTooManyArgs(args, 1)

        val outputData = inputData.datapoints.zipWithNext {
                a, b -> b.copy(value = b.timestamp.toEpochSecond().toDouble() - a.timestamp.toEpochSecond()) }
        return DatapointsValue(outputData, DataType.TIME, inputData.regularity)
    }
}

class TimeBetween2Function : FunctionCallableFromCode("TimeBetween2", R.string.functionsig_timebetween2) {
    override fun main(args: List<Value>): Value {
        val mainData = getArgument<DatapointsValue>(args, 0)
        val referenceData = getArgument<DatapointsValue>(args, 1)
        checkTooManyArgs(args, 2)

        // since we want an exclusive 1 to 1 mapping (meaning not referring to the same ref-point more than once
        // we have to keep track of the points already referenced
        // then we loop through the points in reversed order to prioritise the closer matches
        // (the call-chain contains two reversed()-calls to keep the established order)
        val alreadyReferenced = mutableSetOf<DataPointInterface>()

        val outputData = mainData.datapoints.reversed().map {
            it.let {
                val ref = referenceData.datapoints.firstOrNull { p -> p.timestamp >= it.timestamp }
                if (ref == null || ref in alreadyReferenced) null
                else {
                    alreadyReferenced.add(ref)
                    it.copy(value = ref.timestamp.toEpochSecond().toDouble() - it.timestamp.toEpochSecond())
                }
            }
        }.filterNotNull().reversed()
        return DatapointsValue(outputData, DataType.TIME, mainData.regularity)
    }
}

class FilterFunction : FunctionCallableFromCode("Filter", R.string.functionsig_filter) {
    override fun main(args: List<Value>): Value {
        val inputData = getArgument<DatapointsValue>(args, 0)
        inputData.confirmType(listOf(DataType.CATEGORICAL))
        getArgument<StringValue>(args, 1) // to make sure we have at least one string
        val allowedValues = args.subList(1, args.size).mapIndexed { index, value ->
            when (value) {
                is StringValue -> value.string
                else -> throw WrongArgDatatypeError("Filter", index+1, value::class, listOf(StringValue::class))
            } }
        return DatapointsValue(inputData.datapoints.filter { it.label in allowedValues }, inputData.dataType, inputData.regularity)
    }
}

class ExcludeFunction : FunctionCallableFromCode("Exclude", R.string.functionsig_exclude) {
    override fun main(args: List<Value>): Value {
        val inputData = getArgument<DatapointsValue>(args, 0)
        inputData.confirmType(listOf(DataType.CATEGORICAL))
        getArgument<StringValue>(args, 1) // to make sure we have at least one string
        val allowedValues = args.subList(1, args.size).mapIndexed { index, value ->
            when (value) {
                is StringValue -> value.string
                else -> throw WrongArgDatatypeError("Filter", index+1, value::class, listOf(StringValue::class))
            } }
        return DatapointsValue(inputData.datapoints.filter { it.label !in allowedValues }, inputData.dataType, inputData.regularity)
    }
}

// WORK IN PROGRESS
//class MovingAggregationFunction : FunctionCallableFromCode("aggregateMoving", 0) {
//    override fun main(args: List<Value>): Value {
//        val inputData = getArgument<DatapointsValue>(args, 0)
//        inputData.confirmType(listOf(DataType.NUMERICAL, DataType.TIME))
//        val timeWindow = getArgument<TimeValue>(args, 1)
//        val aggregationFunction = getArgument<AggregationEnumValue>(args, 2)
//
//        return DatapointsValue(
//            MovingAggregator(timeWindow.duration).aggregate(inputData)(aggregationFunction.aggregationFunction) )
//
//    }
//}