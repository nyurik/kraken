package org.wikimedia.analytics.kraken.pig;


import org.apache.pig.EvalFunc;
import org.apache.pig.data.DataType;
import org.apache.pig.data.Tuple;
import org.apache.pig.data.TupleFactory;
import org.apache.pig.impl.logicalLayer.schema.Schema;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Conduct simple referer analysis.
 * Three possible outcomes:
 * DIRECT
 * SEARCH (Google, Bing, etc)
 * 3RD PARTY
 */
public class Referer extends EvalFunc<Tuple> {
    /** Factory to generate Pig tuples */
    private TupleFactory tupleFactory = TupleFactory.getInstance();

    public enum RefererType  {
        /** Manual labour will result in a DIRECT hit */
        DIRECT,

        /** Google, Bing etc */
        SEARCH,

        /** All the rest of the internetz */
        THIRD_PARTY,
    }

    @Override
    public Tuple exec(final Tuple input) throws IOException {
        if (input == null || input.get(0) == null) {
            return null;
        }

        URL referer = null;
        Tuple output = tupleFactory.newTuple(1);

        try {
             referer = new URL((String) input.get(0));
        } catch (MalformedURLException e){
            output.set(0, RefererType.DIRECT);
        }

        if (isSearchReferral(referer)) {
            output.set(0, RefererType.SEARCH);
        } else {
            output.set(0, RefererType.THIRD_PARTY);
        }
        return output;
    }

    /**
     *
     * @param referer
     * @return
     */
    private boolean isSearchReferral(final URL referer) {
        return (referer.getHost().contains("google") || referer.getHost().contains("bing"));
    }

    /**
     *
     * @param input
     * @return
     */
    public final Schema outputSchema(final Schema input) {
        // Check that we were passed two fields
        if (input.size() != 1) {
            throw new RuntimeException(
                    "Expected (chararray), input does not have 1 field");
        }

        try {
            // Get the types for the column and check them.  If it's
            // wrong figure out what type was passed and give a good error
            // message.
            if (input.getField(0).type != DataType.CHARARRAY) {
                String msg = "Expected input (chararray), received schema (";
                msg += DataType.findTypeName(input.getField(0).type);
                msg += ")";
                throw new RuntimeException(msg);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


        return new Schema(new Schema.FieldSchema(null, DataType.BYTE));
    }
}
