/*
 * Copyright (c) 2008-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License
 * (http://www.opensource.org/licenses/mit-license.php)
 */
package hudson.plugins.plot

data class PlotPoint(val yvalue: String?, var url: String?, val label: String?) {
    init {
        if (url.isNullOrBlank()) url = ""
    }
    override fun toString(): String {
        return "$label $url $yvalue"
    }
}
