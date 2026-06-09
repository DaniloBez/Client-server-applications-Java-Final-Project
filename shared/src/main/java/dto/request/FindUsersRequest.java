package dto.request;

public record FindUsersRequest(
        UserFilter filter,
        Sorting sorting,
        Pagination pagination
) {
}
