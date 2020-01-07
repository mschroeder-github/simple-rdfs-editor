package com.github.mschroeder.github.srdfse;

import java.util.Objects;

/**
 *
 * @author Markus Schr&ouml;der
 */
public class Link {
    
    private Resource source;
    private Resource target;

    public Link(Resource source, Resource target) {
        this.source = source;
        this.target = target;
    }

    public Resource getSource() {
        return source;
    }

    public Resource getTarget() {
        return target;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 37 * hash + Objects.hashCode(this.source);
        hash = 37 * hash + Objects.hashCode(this.target);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Link other = (Link) obj;
        if (!Objects.equals(this.source, other.source)) {
            return false;
        }
        if (!Objects.equals(this.target, other.target)) {
            return false;
        }
        return true;
    }

    
    
}
