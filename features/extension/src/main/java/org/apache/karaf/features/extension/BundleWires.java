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
package org.apache.karaf.features.extension;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.resource.Namespace;
import org.osgi.resource.Requirement;

class BundleWires {
	long bundleId;
	Map<String, String> wiring;

	BundleWires(Bundle bundle) {
		this.bundleId = bundle.getBundleId();
		this.wiring = new HashMap<>();
        Map<String, String> bw = new HashMap<>();
        for (BundleWire wire : bundle.adapt(BundleWiring.class).getRequiredWires(null)) {
            bw.put(getRequirementId(wire.getRequirement()), getCapabilityId(wire.getCapability()));
        }
	}
	
	BundleWires(BufferedReader reader) throws IOException {
		Map<String, String> map = new HashMap<>();
		while (true) {
			String key = reader.readLine();
			String val = reader.readLine();
			if (key != null && val != null) {
				map.put(key, val);
			} else {
				break;
			}
		}
	}
    
	void save(Path path) {
		try {
			Files.createDirectories(path);
			Path file = path.resolve(Long.toString(this.bundleId));
			Files.createDirectories(file.getParent());
			try (BufferedWriter fw = Files.newBufferedWriter(file, TRUNCATE_EXISTING, WRITE, CREATE)) {
				for (Map.Entry<String, String> wire : wiring.entrySet()) {
					fw.append(wire.getKey()).append('\n');
					fw.append(wire.getValue()).append('\n');
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
    
	void delete(Path path) {
		try {
			Files.createDirectories(path);
			Path file = path.resolve(Long.toString(this.bundleId));
			Files.deleteIfExists(file);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
    
	long getFragmentHost() {
		return wiring.entrySet().stream()
        .filter(e -> e.getKey().startsWith(HostNamespace.HOST_NAMESPACE))
        .map(Map.Entry::getValue)
        .mapToLong(s -> {
            int idx = s.indexOf(';');
            if (idx > 0) {
                s = s.substring(0, idx);
            }
            return Long.parseLong(s.trim());
        })
        .findFirst()
        .orElse(-1);
	}
	
	void filterMatches(BundleRequirement requirement, Collection<BundleCapability> candidates) {
        String cap = wiring.get(getRequirementId(requirement));
        for (Iterator<BundleCapability> candIter = candidates.iterator(); candIter.hasNext();) {
            BundleCapability cand = candIter.next();
            if (cap != null && !cap.equals(getCapabilityId(cand))
            		|| cap == null && cand.getRevision().getBundle().getBundleId() != this.bundleId) {
                candIter.remove();
            }
        }
	}
	
	private String getRequirementId(Requirement requirement) {
        String filter = requirement.getDirectives().get(Namespace.REQUIREMENT_FILTER_DIRECTIVE);
        if (filter != null) {
            return requirement.getNamespace() + "; " + filter;
        } else {
            return requirement.getNamespace();
        }
    }

    private String getCapabilityId(BundleCapability capability) {
        StringBuilder sb = new StringBuilder(64);
        sb.append(capability.getRevision().getBundle().getBundleId());
        Object v = capability.getAttributes().get(Constants.VERSION_ATTRIBUTE);
        if (v != null) {
            sb.append("; version=").append(v.toString());
        }
        return sb.toString();
    }
}
