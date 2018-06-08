package hudson.plugins.plot

import org.jfree.chart.urls.CategoryURLGenerator
import org.jfree.data.category.CategoryDataset

class PointURLGenerator : CategoryURLGenerator {

    /**
     * Retrieves a URL from the given dataset for a particular item within a
     * series. If the given dataset isn't a PlotCategoryDataset, then null is
     * returned.
     *
     * @param dataset  the dataset
     * @param series   the series index (zero-based)
     * @param category the category index (zero-based)
     * @return the generated URL
     */
    override fun generateURL(dataset: CategoryDataset, series: Int, category: Int): String? {
        return if (dataset is PlotCategoryDataset) {
            dataset.getUrl(series, category)
        } else {
            null
        }
    }
}
