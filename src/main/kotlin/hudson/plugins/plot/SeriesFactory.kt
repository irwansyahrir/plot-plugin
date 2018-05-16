/*
 * Copyright (c) 2008-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License
 * (http://www.opensource.org/licenses/mit-license.php)
 */

package hudson.plugins.plot

import net.sf.json.JSONArray
import net.sf.json.JSONObject
import org.kohsuke.stapler.StaplerRequest

/**
 * This class creates a Series class based on the data source
 *
 * @author areese, Alan.Harder@sun.com
 */
object SeriesFactory {

    /**
     * Using file and label and the Stapler request, create a subclass of series
     * that can process the type selected.
     *
     * @param formData JSON data for series
     */
    fun createSeries(formData: JSONObject, req: StaplerRequest): Series? {
        var formData = formData
        val file = formData.getString("file")
        formData = formData.getJSONObject("fileType")
        formData["file"] = file
        val type = formData.getString("value")
        var typeClass: Class<out Series>? = null

        if ("properties" == type) {
            typeClass = PropertiesSeries::class.java
        } else if ("csv" == type) {
            typeClass = CSVSeries::class.java
        } else if ("xml" == type) {
            typeClass = XMLSeries::class.java
        }

        return if (typeClass != null) req.bindJSON(typeClass, formData) else null
    }

    fun createSeriesList(data: Any, req: StaplerRequest): List<Series> {
        val list = getArray(data)
        val result = mutableListOf<Series>()
        for (series in list) {
            result.add(SeriesFactory.createSeries(series as JSONObject, req)!!)
        }
        return result
    }

    /**
     * Get data as JSONArray (wrap single JSONObject in array if needed).
     */
    fun getArray(data: Any?): JSONArray {
        val result: JSONArray
        if (data is JSONArray) {
            result = data
        } else {
            result = JSONArray()
            if (data != null) {
                result.add(data)
            }
        }
        return result
    }
}
