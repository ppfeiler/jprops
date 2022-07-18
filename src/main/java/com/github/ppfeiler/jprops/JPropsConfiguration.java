package com.github.ppfeiler.jprops;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class JPropsConfiguration {

    private boolean loadProvileFromEnvironment = true;
    private String provileEnvironment = "jprops.profile";

}
