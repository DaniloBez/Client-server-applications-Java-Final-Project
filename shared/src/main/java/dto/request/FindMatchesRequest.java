package dto.request;

public record FindMatchesRequest(
        MatchFilter filter,
        Sorting sorting,
        Pagination pagination
) {
}
