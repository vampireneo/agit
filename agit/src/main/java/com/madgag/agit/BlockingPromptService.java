package com.madgag.agit;

import com.madgag.agit.operations.OpPrompt;

public interface BlockingPromptService {

	<T> T request(OpPrompt<T> opPrompt);
    
}
