/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* $Id$ */

package org.apache.fop.layoutmgr.table;

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.fop.datatypes.PercentBaseContext;
import org.apache.fop.datatypes.Length;

import org.apache.fop.fo.FONode;
import org.apache.fop.fo.flow.Table;
import org.apache.fop.fo.flow.TableColumn;
import org.apache.fop.fo.properties.TableColLength;

/**
 * Class holding a number of columns making up the column setup of a row.
 */
public class ColumnSetup {

    /** Logger **/
    private static Log log = LogFactory.getLog(ColumnSetup.class);

    private Table table;
    private List columns = new java.util.ArrayList();
    private List colWidths = new java.util.ArrayList();
    
    private int maxColIndexReferenced = 0;
    
    /**
     * Main Constructor.
     * @param table the table to construct this column setup for
     */
    public ColumnSetup(Table table) {
        this.table = table;
        prepareColumns();
        initializeColumnWidths();
    }
    
    private void prepareColumns() {
        List rawCols = table.getColumns();
        if (rawCols != null) {
            int colnum = 1;
            ListIterator iter = rawCols.listIterator();
            while (iter.hasNext()) {
                TableColumn col = (TableColumn)iter.next();
                if (col == null) {
                    continue;
                }
                colnum = col.getColumnNumber();
                for (int i = 0; i < col.getNumberColumnsRepeated(); i++) {
                    while (colnum > columns.size()) {
                        columns.add(null);
                    }
                    columns.set(colnum - 1, col);
                    colnum++;
                }
            }
            //Post-processing the list (looking for gaps)
            int pos = 1;
            ListIterator ppIter = columns.listIterator();
            while (ppIter.hasNext()) {
                TableColumn col = (TableColumn)ppIter.next();
                if (col == null) {
                    log.error("Found a gap in the table-columns at position " + pos);
                }
                pos++;
            }
        }
    }

    /**
     * Returns a column. If the index of the column is bigger than the number of explicitly
     * defined columns the last column is returned.
     * @param index index of the column (1 is the first column)
     * @return the requested column
     */
    public TableColumn getColumn(int index) {
        int size = columns.size();
        if (index > size) {
            if (index > maxColIndexReferenced) {
                maxColIndexReferenced = index;
                if (!(size == 1 && getColumn(1).isDefaultColumn())) {
                    log.warn(FONode.decorateWithContextInfo(
                            "There are fewer table-columns than are needed. "
                            + "Column " + index + " was accessed, but only "
                            + size + " columns have been defined. "
                            + "The last defined column will be reused."
                        , table));
                    if (!table.isAutoLayout()) {
                        log.warn("Please note that according XSL-FO 1.0 (7.26.9) says that "
                                + "the 'column-width' property must be specified for every "
                                + "column, unless the automatic table layout is used.");
                    }
                }
            }
            return (TableColumn) columns.get(size - 1);
        } else {
            return (TableColumn) columns.get(index - 1);
        }
    }
 
    /** @see java.lang.Object#toString() */
    public String toString() {
        return columns.toString();
    }

    /** @return the number of columns in the setup. */
    public int getColumnCount() {
        if (maxColIndexReferenced > columns.size()) {
            return maxColIndexReferenced;
        } else {
            return columns.size();
        }
   }
    
    /** @return an Iterator over all columns */
    public Iterator iterator() {
        return this.columns.iterator();
    }
    
    /*
    private void createColumnsFromFirstRow() {
        //TODO Create oldColumns from first row here 
        //--> rule 2 in "fixed table layout", see CSS2, 17.5.2
        //Alternative: extend oldColumns on-the-fly, but in this case we need the
        //new property evaluation context so proportional-column-width() works
        //correctly.
        if (columns.size() == 0) {
            this.columns.add(table.getDefaultColumn());
        }
    }
    */

    /**
     * Initializes the column's widths
     * 
     */
    private void initializeColumnWidths() {
        
        TableColumn col;
        Length colWidth;
        
        for (int i = columns.size(); --i >= 0;) {
            if (columns.get(i) != null) {
                col = (TableColumn) columns.get(i);
                colWidth = col.getColumnWidth();
                colWidths.add(0, colWidth);
            }
        }
        colWidths.add(0, null);
    }
    
    /**
     * Works out the base unit for resolving proportional-column-width()
     * [p-c-w(x) = x * base_unit_ipd]
     * 
     * @param tlm   the TableLayoutManager
     * @return the computed base unit (in millipoint)
     */
    protected double computeTableUnit(TableLayoutManager tlm) {
        
        int sumCols = 0;
        float factors = 0;
        double unit = 0;
        
        /* calculate the total width (specified absolute/percentages), 
         * and work out the total number of factors to use to distribute
         * the remaining space (if any)
         */
        for (Iterator i = colWidths.iterator(); i.hasNext();) {
            Length colWidth = (Length) i.next();
            if (colWidth != null) {
                sumCols += colWidth.getValue(tlm);
                if (colWidth instanceof TableColLength) {
                    factors += 
                        ((TableColLength) colWidth).getTableUnits();
                }
            }
        }
        
        /* distribute the remaining space over the accumulated 
         * factors (if any) 
         */
        if (factors > 0) {
            if (sumCols < tlm.getContentAreaIPD()) {
                unit = (tlm.getContentAreaIPD() - sumCols) / factors;
            } else {
                log.warn("No space remaining to distribute over columns.");
            }
        }
        
        return unit;
    }
    
    /**
     * @param col column index (1 is first column)
     * @param context the context for percentage based calculations
     * @return the X offset of the requested column
     */
    public int getXOffset(int col, PercentBaseContext context) {
        int xoffset = 0;
        for (int i = col; --i >= 0;) {
            if (colWidths.get(i) != null) {
                xoffset += ((Length) colWidths.get(i)).getValue(context);
            }
        }
        return xoffset;
    }

}
