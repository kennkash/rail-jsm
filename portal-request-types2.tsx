/* Inside portal-request-types.tsx */

const apiRequestTypes = useMemo<RequestTypeOption[] | null>(() => {
  if (!data?.requestTypes?.length) return null;

  const mappedTypes = data.requestTypes.map((type) => ({
    ...type,
    displayOrder: typeof type.displayOrder === 'number' ? type.displayOrder : 999, // Fallback to end
  }));

  return [...mappedTypes].sort((a, b) => {
    // Primary sort: displayOrder
    if (a.displayOrder !== b.displayOrder) {
      return a.displayOrder - b.displayOrder;
    }
    // Secondary sort: Name
    return a.name.localeCompare(b.name);
  });
}, [data, jsmIconUrls]);

// Replace the existing availableGroups logic with this:
const groupedSections = useMemo(() => {
  // Use the groups from API as the primary source for order
  const orderSource = data?.groups || [];
  
  if (orderSource.length > 0) {
    return orderSource
      .map(g => ({
        name: g.name,
        types: groupedTypes[g.name] ?? []
      }))
      .filter(section => section.types.length > 0);
  }

  // Fallback to existing logic if no groups returned
  return Object.keys(groupedTypes).map(name => ({
    name,
    types: groupedTypes[name]
  }));
}, [data?.groups, groupedTypes]);

