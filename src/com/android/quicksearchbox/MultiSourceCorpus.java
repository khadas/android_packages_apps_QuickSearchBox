/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.quicksearchbox;


import com.android.quicksearchbox.util.BarrierConsumer;
import com.android.quicksearchbox.util.NamedTaskExecutor;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Base class for corpora backed by multiple sources.
 */
public abstract class MultiSourceCorpus extends AbstractCorpus {

    private final Context mContext;

    private final NamedTaskExecutor mExecutor;

    private final ArrayList<Source> mSources;

    public MultiSourceCorpus(Context context, NamedTaskExecutor executor, Source... sources) {
        mContext = context;
        mExecutor = executor;

        mSources = new ArrayList<Source>();
        for (Source source : sources) {
            mSources.add(source);
        }
    }

    protected Context getContext() {
        return mContext;
    }

    public Collection<Source> getSources() {
        return mSources;
    }

    /**
     * Creates a corpus result object for a set of source results.
     * This method should not call {@link Result#fill}.
     *
     * @param query The query text.
     * @param results The results of the queries.
     * @return An instance of {@link Result} or a subclass of it.
     */
    protected abstract Result createResult(String query, ArrayList<SourceResult> results);

    public CorpusResult getSuggestions(String query, int queryLimit) {
        BarrierConsumer<SourceResult> consumer =
                new BarrierConsumer<SourceResult>(mSources.size());
        QueryTask.startQueries(query, queryLimit, mSources, mExecutor,
                null, consumer);
        ArrayList<SourceResult> results = consumer.getValues();
        Result result = createResult(query, results);
        result.fill();
        return result;
    }

    /**
     * Base class for results returned by {@link MultiSourceCorpus#getSuggestions}.
     * Subclasses of {@link MultiSourceCorpus} should override
     * {@link MultiSourceCorpus#createResult} and return an instance of this class or a
     * subclass.
     */
    protected class Result extends ListSuggestionCursor implements CorpusResult {

        private final ArrayList<SourceResult> mResults;

        public Result(String userQuery, ArrayList<SourceResult> results) {
            super(userQuery);
            mResults = results;
        }

        protected ArrayList<SourceResult> getResults() {
            return mResults;
        }

        /**
         * Fills the list of suggestions using the list of results.
         * The default implementation concatenates the results.
         */
        public void fill() {
            for (SourceResult result : getResults()) {
                int count = result.getCount();
                for (int i = 0; i < count; i++) {
                    result.moveTo(i);
                    add(new SuggestionPosition(result));
                }
            }
        }

        public Corpus getCorpus() {
            return MultiSourceCorpus.this;
        }

        @Override
        public void close() {
            super.close();
            for (SourceResult result : mResults) {
                result.close();
            }
        }

        @Override
        public String toString() {
            return getCorpus() + "[" + getUserQuery() + "]";
        }
    }

}
