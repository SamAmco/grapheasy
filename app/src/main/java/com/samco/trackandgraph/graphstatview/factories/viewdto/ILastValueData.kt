package com.samco.trackandgraph.graphstatview.factories.viewdto

import com.samco.trackandgraph.base.database.dto.DataPoint
import com.samco.trackandgraph.base.database.dto.GraphOrStat

interface ILastValueData : IGraphStatViewData {
    val isDuration: Boolean
    val lastDataPoint: DataPoint?
        get() = null

    companion object {
        fun loading(graphOrStat: GraphOrStat) = object : ILastValueData {
            override val isDuration: Boolean
                get() = false
            override val state: IGraphStatViewData.State
                get() = IGraphStatViewData.State.LOADING
            override val graphOrStat: GraphOrStat
                get() = graphOrStat
        }
    }
}