package com.nci.backend.history;
import com.nci.backend.provider.model.*;
public interface HistoricalProvider {
    ProviderResult fetchHistory(HistoryQuery query);
    ProviderMetadata metadata();
}
