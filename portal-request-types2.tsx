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
