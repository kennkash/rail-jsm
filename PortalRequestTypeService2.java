/* Inside PortalRequestTypeService.java */

private RequestTypesResponseDTO buildResponseFromRequestTypes(Project project, ServiceDesk serviceDesk, List<RequestType> requestTypes) {
    List<RequestTypeDTO> dtoList = new ArrayList<>();
    Map<Integer, RequestTypeGroupDTO> groupMap = new LinkedHashMap<>();

    for (RequestType requestType : requestTypes) {
        RequestTypeDTO dto = convertToDto(requestType, project, serviceDesk);

        if (requestType.getGroups() != null && !requestType.getGroups().isEmpty()) {
            for (RequestTypeGroup group : requestType.getGroups()) {
                // 1. Handle Group Ordering
                RequestTypeGroupDTO groupDTO = groupMap.computeIfAbsent(
                        group.getId(),
                        id -> {
                            RequestTypeGroupDTO dtoGroup = new RequestTypeGroupDTO(String.valueOf(id), group.getName());
                            dtoGroup.setServiceDeskId(String.valueOf(serviceDesk.getId()));
                            // This sets the order of the TABS/Groups themselves
                            group.getOrder().ifPresent(dtoGroup::setDisplayOrder); 
                            dtoGroup.setRequestTypeCount(0);
                            return dtoGroup;
                        }
                );

                groupDTO.setRequestTypeCount((groupDTO.setRequestTypeCount() == null ? 0 : groupDTO.setRequestTypeCount()) + 1);
                dto.getGroupIds().add(String.valueOf(group.getId()));
                dto.getGroups().add(groupDTO.getName());
                
                // 2. FIX: Handle Request Type Ordering within this group
                // JSM API provides order via group.getRequestTypeOrder(requestType)
                // Note: Optional<Integer> order = group.getOrder(); refers to the group itself.
                // To get the RT order within the group, we use the specific method:
                group.getRequestTypeOrder(requestType.getId()).ifPresent(order -> {
                    // We store this in the DTO. Note: If RT is in multiple groups, 
                    // this simple DTO might need logic to pick the "primary" group order.
                    dto.setDisplayOrder(order); 
                });

                if (dto.getGroup() == null) {
                    dto.setGroup(groupDTO.getName());
                }
            }
        }
        dtoList.add(dto);
    }
    
    // 3. Sort the final lists before returning
    // Sort groups by their display order
    List<RequestTypeGroupDTO> sortedGroups = new ArrayList<>(groupMap.values());
    sortedGroups.sort((a, b) -> Integer.compare(a.getDisplayOrder() != null ? a.getDisplayOrder() : 0, 
                                               b.getDisplayOrder() != null ? b.getDisplayOrder() : 0));

    RequestTypesResponseDTO response = new RequestTypesResponseDTO(dtoList, sortedGroups);
    // ... rest of method
}
